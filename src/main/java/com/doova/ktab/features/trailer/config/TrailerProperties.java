package com.doova.ktab.features.trailer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "ktab.trailer")
public class TrailerProperties {

    private boolean enabled = false;

    /** Control-plane IDs printed by ops/trailer-agent/setup.sh. */
    private String agentId;
    private int agentVersion;
    private String environmentId;
    private String vaultId;
    private String workspace = "default";

    private String anthropicApiKey;
    private String webhookSigningKey;

    private String voiceId;
    /** D4: list-cost cap per session in US cents, as the API expects ("2000" = $20.00). */
    private long budgetCents = 2000;
    private int maxOutcomeIterations = 3;
    /** D5: soft limit on generations where an advisory message is sent to the agent to finalize. */
    private int softHiggsfieldGenerations = 14;
    /** D5: jobs actually created — the money limit. */
    private int maxHiggsfieldGenerations = 16;
    /** Video jobs the agent may create (single-shot: 1 film + whole-film re-rolls). */
    private int maxVideoJobs = 3;
    /** D5: generate calls of any outcome — a runaway guard against a loop of rejected/suggestion replies. */
    private int maxHiggsfieldCalls = 40;
    /** When true, a trailer whose video and audio streams are fully verified by ffprobe is promoted to READY even if qc_report.json is missing. */
    private boolean autoPromoteValidMediaWithoutQc = true;
    private String higgsfieldGenerationMarker = "generate";
    /**
     * Matches the text of a job-created generate_video reply. Verified against a real session (2026-09-29):
     * a created job always replies {"results":[{"id":...,"status":"pending",...}]}. Both fragments are required
     * because a rejected preset-recommendation notice also carries an "id" (the *preset's* id, not a job), which
     * a bare "id" match would have miscounted as a job.
     */
    private String higgsfieldJobPattern = "(?s)(?=.*\"results\")(?=.*\"status\"\\s*:\\s*\"pending\")";
    /**
     * Exact generate_video arguments the agent must use so no call comes back as a preset suggestion. Default
     * captured from a real session that created jobs on the first try with these exact arguments.
     */
    private String higgsfieldGenerateArgs = "model=seedance_2_5, mode=t2v, aspect_ratio=16:9, resolution=1080p, "
            + "duration=30, generate_audio=false";
    /** Generations the agent may keep queued or running at once; keep below the account's concurrency limit. */
    private int higgsfieldMaxInFlight = 2;

    private String higgsfieldMcpUrl = "https://mcp.higgsfield.ai/mcp";
    private String higgsfieldCallbackUrl;

    private String ffprobePath = "ffprobe";
    private Duration reconcileEvery = Duration.ofSeconds(60);
    private Duration launchTimeout = Duration.ofMinutes(5);
    /** A session still running after this is interrupted and failed. */
    private Duration maxSessionAge = Duration.ofHours(2);
    private int perBookPer30Days = 3;
    /** Max concurrent RUNNING sessions allowed across the system. */
    private int maxConcurrentRuns = 4;
}
