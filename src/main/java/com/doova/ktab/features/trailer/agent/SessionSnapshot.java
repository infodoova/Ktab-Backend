package com.doova.ktab.features.trailer.agent;

/**
 * higgsfieldGenerations counts jobs Higgsfield actually created (the money limit); higgsfieldCalls counts every
 * generate call, including ones rejected by a concurrency limit or answered with a preset suggestion (D5). videoJobs counts only the created generate_video jobs (the expensive 30 s unit).
 */
public record SessionSnapshot(Phase phase, String stopReason, String outcomeResult, String outcomeExplanation,
                              int higgsfieldGenerations, int higgsfieldCalls, int videoJobs) {

    /** Kept for callers that do not track video jobs separately. */
    public SessionSnapshot(Phase phase, String stop, String result, String explanation, int generations, int calls) {
        this(phase, stop, result, explanation, generations, calls, 0);
    }

    public enum Phase {
        /** Agent still working (running, rescheduling, or transiently idle between steps). */
        RUNNING,
        /** Idle with a terminal stop reason (end_turn / retries_exhausted); read outcomeResult. */
        FINISHED,
        /** Idle waiting for a tool confirmation — should never happen with always_allow (Review Focus 1). */
        NEEDS_ACTION,
        /** Paused at the session budget; only a budget change resumes it (Review Focus 2). */
        BUDGET_REACHED,
        TERMINATED
    }
}
