package com.doova.ktab.features.studio.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for the independent Studio ingestion + audiobook pipeline
 * ({@code features.studio}). Does not extend or reference {@code OcrProperties} —
 * the two pipelines are independent (docs/ocr_engine_v3.md, "Two independent pipelines").
 */
@Configuration
@ConfigurationProperties(prefix = "ktab.studio")
@Getter
@Setter
public class StudioProperties {

    private String apiKey;
    private String baseUrl = "https://api.elevenlabs.io";

    private String defaultModelId = "eleven_v3";
    private String defaultTitleVoiceId;
    private String defaultParagraphVoiceId;
    private String qualityPreset = "high";

    /** Synthetic pagination target for projected digital-book pages (docs Phase 3.6). */
    private int pageTargetChars = 1800;

    private ProjectionGate projectionGate = new ProjectionGate();
    private Sync sync = new Sync();
    private Audiobook audiobook = new Audiobook();

    @Getter
    @Setter
    public static class ProjectionGate {
        private int minChapters = 2;
        private int maxChapters = 500;
        /** A single chapter exceeding this fraction of total characters fails the gate. */
        private double maxChapterCharRatio = 0.15;
        /** Allowed deviation between Studio's total characters and the PDF's own text-layer count. */
        private double charCountToleranceRatio = 0.20;
    }

    @Getter
    @Setter
    public static class Sync {
        /** Tier 1 (status) polling backoff during active conversion. */
        private Duration pollIntervalMin = Duration.ofSeconds(3);
        private Duration pollIntervalMax = Duration.ofSeconds(30);
        private Duration pollTimeout = Duration.ofMinutes(30);
    }

    @Getter
    @Setter
    public static class Audiobook {
        /** Hard ceiling; estimateStep fails the job rather than converting past this. */
        private long maxCharsPerBook = 1_500_000;
        /** Global concurrency cap across all books converting at once (see DynamicConcurrencyGate). */
        private int maxConcurrentConversions = 2;
        /** When true, every step runs except the actual /convert call. */
        private boolean dryRun = false;
    }
}
