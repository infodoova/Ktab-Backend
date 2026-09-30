package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.SessionSnapshot;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** RUNNING -> HARVESTING / FAILED. Works without webhooks (D6); webhooks only pull nextCheckAt forward. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrailerReconciler {

    private final BookTrailerRepository trailers;
    private final TrailerAgentGateway gateway;
    private final TrailerProperties properties;
    private final TrailerNotifier notifier;

    public void reconcile(Long trailerId) {
        BookTrailer t = trailers.findById(trailerId).orElseThrow();
        if (t.getStatus() != TrailerStatus.RUNNING || t.getSessionId() == null) {
            return;
        }
        SessionSnapshot s = gateway.snapshot(t.getSessionId());
        t.setHiggsfieldGenerations(s.higgsfieldGenerations());
        t.setHiggsfieldCalls(s.higgsfieldCalls());

        // D7: billing alert — low credits means Anthropic will archive the session; ops must top up immediately.
        if (s.outcomeExplanation() != null && s.outcomeExplanation().contains("credit balance is too low")) {
            log.error("CRITICAL BILLING ALERT: Anthropic API credit balance is too low for session {}. "
                    + "Please recharge credits immediately to avoid losing in-flight trailer sessions.",
                    t.getSessionId());
        }

        if (s.higgsfieldGenerations() > properties.getMaxHiggsfieldGenerations()) { // D5: the money limit
            stop(t, "Stopped: the agent created " + s.higgsfieldGenerations() + " Higgsfield jobs, over the cap of "
                    + properties.getMaxHiggsfieldGenerations() + ".");
            return;
        }
        if (s.videoJobs() > properties.getMaxVideoJobs()) { // single-shot: each 30 s job is the expensive unit
            stop(t, "Stopped: the agent created " + s.videoJobs() + " video jobs, over the cap of "
                    + properties.getMaxVideoJobs() + ".");
            return;
        }
        if (s.higgsfieldCalls() > properties.getMaxHiggsfieldCalls()) { // D5: runaway guard against rejected-call loops
            stop(t, "Stopped: the agent made " + s.higgsfieldCalls() + " Higgsfield generate calls, over the cap of "
                    + properties.getMaxHiggsfieldCalls() + " calls; it is probably retrying rejected calls in a loop.");
            return;
        }
        switch (s.phase()) {
            case RUNNING -> {
                if (t.getStartedAt() != null && t.getStartedAt().plus(properties.getMaxSessionAge()).isBefore(Instant.now())) {
                    stop(t, "Stopped: the session ran longer than " + properties.getMaxSessionAge() + ".");
                    return;
                }
                // D5: soft-cap advisory — nudge the agent to wrap up before the hard cap kills it.
                // Use == so the message is sent exactly once, the first tick after crossing the threshold.
                if (s.higgsfieldGenerations() == properties.getSoftHiggsfieldGenerations()) {
                    log.info("trailer {} soft cap reached ({}/{} jobs); sending wrap-up advisory to agent",
                            t.getId(), s.higgsfieldGenerations(), properties.getSoftHiggsfieldGenerations());
                    gateway.sendMessage(t.getSessionId(),
                            "You have reached your primary generation allowance (" + s.higgsfieldGenerations()
                                    + " jobs). Do not submit further generation jobs. "
                                    + "Finish the trailer with the footage you have already accepted.");
                }
                t.setNextCheckAt(Instant.now().plus(properties.getReconcileEvery()));
                trailers.save(t);
            }
            case FINISHED, TERMINATED -> toHarvest(t, s);
            case NEEDS_ACTION -> stop(t, "The agent is waiting for a tool approval. Set permission_policy always_allow on "
                    + "the agent's toolsets (ops/trailer-agent/agent.json) and publish a new agent version.");
            case BUDGET_REACHED -> {
                boolean hasTrailer = gateway.outputs(t.getSessionId()).stream()
                        .anyMatch(f -> "trailer.mp4".equals(f.filename()));
                if (hasTrailer) {
                    s = new SessionSnapshot(s.phase(), s.stopReason(), "budget_reached",
                            "Session budget reached before the grader was satisfied.", s.higgsfieldGenerations(),
                            s.higgsfieldCalls());
                    toHarvest(t, s);
                } else {
                    fail(t, "The session reached its $" + (properties.getBudgetCents() / 100.0)
                            + " budget before producing a trailer.");
                }
            }
        }
    }

    private void toHarvest(BookTrailer t, SessionSnapshot s) {
        t.setOutcomeResult(s.outcomeResult());
        t.setOutcomeExplanation(s.outcomeExplanation());
        t.setStatus(TrailerStatus.HARVESTING);
        trailers.save(t);
    }

    private void stop(BookTrailer t, String reason) {
        try {
            gateway.interrupt(t.getSessionId());
        } catch (RuntimeException e) {
            log.warn("interrupt of {} failed: {}", t.getSessionId(), e.getMessage());
        }
        // D7: media-first salvage — if the session already produced trailer.mp4, harvest it instead of failing.
        boolean hasTrailer = gateway.outputs(t.getSessionId()).stream()
                .anyMatch(f -> "trailer.mp4".equals(f.filename()));
        if (hasTrailer) {
            log.warn("trailer {} was stopped ({}), but trailer.mp4 exists in session outputs; routing to HARVESTING",
                    t.getId(), reason);
            SessionSnapshot salvage = new SessionSnapshot(
                    SessionSnapshot.Phase.TERMINATED, "stopped", "salvaged",
                    "Stopped with reason: " + reason + ". Salvaged because trailer.mp4 was found.",
                    t.getHiggsfieldGenerations(), t.getHiggsfieldCalls());
            toHarvest(t, salvage);
        } else {
            fail(t, reason);
        }
    }

    private void fail(BookTrailer t, String reason) {
        t.setStatus(TrailerStatus.FAILED);
        t.setError(reason);
        t.setFinishedAt(Instant.now());
        trailers.save(t);
        notifier.notifyCompletion(t);
    }
}
