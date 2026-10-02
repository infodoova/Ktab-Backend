package com.doova.ktab.features.trailer.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure interpretation of a session's status + events, so the gate logic is unit-tested without the SDK. */
public final class SessionEvents {

    public record OutcomeResult(String result, String explanation) {
    }

    private SessionEvents() {
    }

    /**
     * D5: a generate_video call only counts as a Higgsfield job (the money limit) once its result is paired
     * (by {@code mcp_tool_use_id}) with a non-error reply whose text matches {@code jobPattern}. A call rejected
     * by Higgsfield's concurrency limit, or answered with a preset suggestion instead of a job, still counts
     * toward {@code higgsfieldCalls} (the runaway guard) but not toward {@code higgsfieldGenerations}.
     */
    public static SessionSnapshot interpret(String sessionStatus, List<JsonNode> events, List<OutcomeResult> outcomes,
                                            String generationMarker, Pattern jobPattern) {
        int generations = 0;
        int calls = 0;
        int videoJobs = 0;
        Set<String> generateCallIds = new HashSet<>();
        Set<String> videoCallIds = new HashSet<>();
        String lastIdleStop = null;
        String marker = generationMarker.toLowerCase(Locale.ROOT);
        for (JsonNode e : events) {
            String type = e.path("type").asText();
            if ("agent.mcp_tool_use".equals(type)
                    && e.path("name").asText().toLowerCase(Locale.ROOT).contains(marker)) {
                calls++;
                generateCallIds.add(e.path("id").asText());
                if (e.path("name").asText().toLowerCase(Locale.ROOT).contains("generate_video")) {
                    videoCallIds.add(e.path("id").asText());
                }
            } else if ("agent.mcp_tool_result".equals(type)
                    && generateCallIds.contains(e.path("mcp_tool_use_id").asText())
                    && !e.path("is_error").asBoolean(false)
                    && jobPattern.matcher(resultText(e)).find()) {
                generations++;
                if (videoCallIds.contains(e.path("mcp_tool_use_id").asText())) {
                    videoJobs++;
                }
            } else if ("session.status_idle".equals(type)) {
                lastIdleStop = e.path("stop_reason").path("type").asText(null);
            } else if ("session.status_running".equals(type)) {
                lastIdleStop = null; // a later run supersedes an earlier idle
            }
        }
        OutcomeResult outcome = outcomes.isEmpty() ? null : outcomes.get(outcomes.size() - 1);
        String result = outcome == null ? null : outcome.result();
        String explanation = outcome == null ? null : outcome.explanation();

        SessionSnapshot.Phase phase;
        if ("terminated".equals(sessionStatus)) {
            phase = SessionSnapshot.Phase.TERMINATED;
        } else if (!"idle".equals(sessionStatus) || lastIdleStop == null) {
            phase = SessionSnapshot.Phase.RUNNING;
        } else if ("requires_action".equals(lastIdleStop)) {
            phase = SessionSnapshot.Phase.NEEDS_ACTION;
        } else if ("budget_reached".equals(lastIdleStop)) {
            phase = SessionSnapshot.Phase.BUDGET_REACHED;
        } else {
            phase = SessionSnapshot.Phase.FINISHED; // end_turn, retries_exhausted
        }
        return new SessionSnapshot(phase, lastIdleStop, result, explanation, generations, calls, videoJobs);
    }

    /**
     * The tool result's actual text, unescaped. {@code content} is {@code [{"type":"text","text":"..."}]}; naively
     * calling {@code JsonNode.toString()} on it re-serializes the array and doubles every escape inside "text"
     * (Higgsfield's own {@code "results":...} becomes the literal substring {@code \"results\"}), which silently
     * defeats a jobPattern written against the real reply text. Concatenating each block's unescaped {@code .asText()}
     * value is the fix; verified 2026-09-29 against a real session's actual replies.
     */
    private static String resultText(JsonNode resultEvent) {
        StringBuilder text = new StringBuilder();
        for (JsonNode block : resultEvent.path("content")) {
            text.append(block.path("text").asText(""));
        }
        return text.toString();
    }
}
