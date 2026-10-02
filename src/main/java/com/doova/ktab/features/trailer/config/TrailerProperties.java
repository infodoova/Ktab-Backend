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

    /**
     * Optional JSON catalog of approved narration voices, e.g.
     * {@code [{"id":"...","name":"Sami","suits":"politics, history"}]} or the plain form
     * {@code id|name|suits;id|name|suits}. When present the agent picks the
     * voice that best fits the book. There is no single default voice: the list is the only source.
     */
    private String voices;
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

    /** Parses {@link #voices}; a broken catalog fails loudly rather than silently using the default voice. */
    public java.util.List<TrailerVoice> voiceCatalog() {
        if (voices == null || voices.isBlank()) {
            return java.util.List.of();
        }
        if (!voices.stripLeading().startsWith("[")) {
            return parsePlainCatalog(voices);
        }
        try {
            java.util.List<TrailerVoice> catalog = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(voices, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<TrailerVoice>>() { });
            for (TrailerVoice v : catalog) {
                if (v.id() == null || v.id().isBlank()) {
                    throw new IllegalStateException("KTAB_TRAILER_VOICES: every voice needs an id");
                }
            }
            return catalog;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("KTAB_TRAILER_VOICES is not a valid JSON array of {id, name, suits}: "
                    + e.getOriginalMessage(), e);
        }
    }

    /** {@code id|name|suits;id|name|suits}: survives .env files and Docker env, which mangle JSON quotes. */
    private static java.util.List<TrailerVoice> parsePlainCatalog(String text) {
        java.util.List<TrailerVoice> catalog = new java.util.ArrayList<>();
        for (String entry : text.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            String[] f = entry.split("[|]", 3);
            String id = f[0].strip();
            if (id.isEmpty()) {
                throw new IllegalStateException("KTAB_TRAILER_VOICES: every voice needs an id");
            }
            catalog.add(new TrailerVoice(id, f.length > 1 ? f[1].strip() : null, f.length > 2 ? f[2].strip() : null));
        }
        return catalog;
    }
}
