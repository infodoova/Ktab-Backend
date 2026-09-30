package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.SessionSnapshot;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TrailerReconcilerTest {

    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final TrailerProperties props = new TrailerProperties();
    final TrailerNotifier notifier = mock(TrailerNotifier.class);
    final TrailerReconciler reconciler = new TrailerReconciler(trailers, gateway, props, notifier);
    final BookTrailer t = new BookTrailer();

    @BeforeEach
    void setUp() {
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.RUNNING);
        t.setSessionId("sesn_1");
        t.setStartedAt(Instant.now());
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void snapshot(SessionSnapshot.Phase phase, String stop, String outcome, int generations) {
        snapshot(phase, stop, outcome, generations, generations);
    }

    private void snapshot(SessionSnapshot.Phase phase, String stop, String outcome, int generations, int calls) {
        when(gateway.snapshot("sesn_1")).thenReturn(new SessionSnapshot(phase, stop, outcome, "why", generations, calls));
    }

    @Test
    void videoJobsOverTheVideoAllowanceStopTheSession() {
        props.setMaxVideoJobs(3);
        when(gateway.snapshot("sesn_1")).thenReturn(new SessionSnapshot(SessionSnapshot.Phase.RUNNING, null, null,
                "why", 8, 8, 4));
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(t.getError()).contains("4 video jobs, over the cap of 3");
    }

    @Test
    void reconcilerFinishesWithoutAnyWebhook() {
        snapshot(SessionSnapshot.Phase.FINISHED, "end_turn", "satisfied", 5);

        reconciler.reconcile(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.HARVESTING);
        assertThat(t.getOutcomeResult()).isEqualTo("satisfied");
        assertThat(t.getHiggsfieldGenerations()).isEqualTo(5);
    }

    @Test
    void stillRunningIsCheckedAgainLater() {
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 2);

        reconciler.reconcile(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
        assertThat(t.getNextCheckAt()).isAfter(Instant.now());
    }

    @Test
    void tooManyHiggsfieldGenerationsInterruptsTheSession() {
        props.setMaxHiggsfieldGenerations(8);
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 9); // job cap 8
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(t.getError()).contains("Higgsfield").contains("8");
    }

    @Test
    void aLoopOfRejectedCallsHitsTheCallCap() {
        props.setMaxHiggsfieldCalls(140);
        // 141 generate calls but only 5 jobs: the agent is stuck retrying rejected calls (D5).
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 5, 141);
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(t.getError()).contains("calls").contains("140");
    }

    @Test
    void rejectedCallsBelowTheCapDoNotStopAGoodRun() {
        // 7 jobs from 11 calls, as in the first real run: must keep running under both caps.
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 7, 11);

        reconciler.reconcile(7L);

        verify(gateway, never()).interrupt(any(String.class));
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
        assertThat(t.getHiggsfieldCalls()).isEqualTo(11);
    }

    @Test
    void requiresActionFailsTheTrailerWithAPermissionHint() {
        snapshot(SessionSnapshot.Phase.NEEDS_ACTION, "requires_action", null, 1);
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(t.getError()).contains("always_allow");
    }

    @Test
    void budgetReachedWithoutAnOutputFails() {
        snapshot(SessionSnapshot.Phase.BUDGET_REACHED, "budget_reached", null, 4);
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(t.getError()).contains("budget");
    }

    @Test
    void budgetReachedWithATrailerIsHarvestedForReview() {
        snapshot(SessionSnapshot.Phase.BUDGET_REACHED, "budget_reached", null, 4);
        when(gateway.outputs("sesn_1")).thenReturn(List.of(new TrailerAgentGateway.OutputFile("f1", "trailer.mp4", 10)));

        reconciler.reconcile(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.HARVESTING);
    }

    @Test
    void aSessionOlderThanTheMaximumAgeIsStopped() {
        t.setStartedAt(Instant.now().minus(props.getMaxSessionAge()).minusSeconds(60));
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 3);
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
    }

    @Test
    void cancelledTrailerIsLeftAlone() {
        t.setStatus(TrailerStatus.CANCELLED);

        reconciler.reconcile(7L);

        verifyNoInteractions(gateway);
    }

    @Test
    void softCapSendsAdvisoryMessageToAgent() {
        props.setSoftHiggsfieldGenerations(10);
        props.setMaxHiggsfieldGenerations(15);
        // Exactly at the soft cap while still running — must send advisory.
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 10);
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        verify(gateway).sendMessage(eq("sesn_1"), contains("primary generation allowance"));
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
    }

    @Test
    void softCapNotSentWhenBelowThreshold() {
        props.setSoftHiggsfieldGenerations(10);
        props.setMaxHiggsfieldGenerations(15);
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 6); // below soft cap
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        verify(gateway, never()).sendMessage(any(String.class), any(String.class));
    }

    @Test
    void stopSalvagesToHarvestingWhenTrailerMp4Exists() {
        // Agent created too many jobs, but trailer.mp4 already exists — should harvest, not fail.
        props.setMaxHiggsfieldGenerations(8);
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 9);
        when(gateway.outputs("sesn_1")).thenReturn(
                List.of(new TrailerAgentGateway.OutputFile("f1", "trailer.mp4", 26_000_000)));

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.HARVESTING);
        assertThat(t.getOutcomeResult()).isEqualTo("salvaged");
    }

    @Test
    void stopFailsNormallyWhenNoTrailerMp4() {
        props.setMaxHiggsfieldGenerations(8);
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 9);
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
    }

    @Test
    void billingAlertDoesNotDisruptNormalReconcileFlow() {
        // outcomeExplanation containing the low-credits phrase must log an ERROR but not throw.
        when(gateway.snapshot("sesn_1")).thenReturn(new SessionSnapshot(
                SessionSnapshot.Phase.RUNNING, null, null,
                "credit balance is too low to continue", 3, 3));
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        // Session is still running; reconciler schedules a later check without throwing.
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
    }
}
