package com.doova.ktab.features.storybook.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the personalized storybook feature. Prices are the spec's
 * 2026-09-24 snapshot; re-check provider pricing before launch.
 * Map keys contain dots, so override them with bracket syntax:
 * {@code ktab.storybook.pricing.image-per-image-usd[gemini-3.1-flash-image]=0.067}.
 */
@Configuration
@ConfigurationProperties(prefix = "ktab.storybook")
@Getter
@Setter
public class StorybookProperties {

    /** Master switch for the worker and the public endpoints. */
    private boolean enabled = false;

    private Llm llm = new Llm();
    private Image image = new Image();
    private Pricing pricing = new Pricing();
    private Limits limits = new Limits();
    private Worker worker = new Worker();
    private Photo photo = new Photo();
    private Credits credits = new Credits();

    @Getter
    @Setter
    public static class Credits {
        /** false only for internal testing: approvals then never touch credits. */
        private boolean required = true;
        private int unitsPerBook = 1;
    }

    @Getter
    @Setter
    public static class Photo {
        /** Base64 of 32 random bytes (AES-256). Env KTAB_STORYBOOK_PHOTO_KEY. Never commit it. */
        private String encryptionKey;
        private long maxBytes = 10L * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Llm {
        private String provider = "OPENAI";
        private String model = "gpt-6-luna";
        private Duration timeout = Duration.ofMinutes(5);
        private int maxRetries = 2;
        /** Read from ANTHROPIC_API_KEY or OPENAI_API_KEY. Never commit a key. */
        private String apiKey;
    }

    @Getter
    @Setter
    public static class Image {
        private String primaryModel = "gemini-3.1-flash-image";
        private String fallbackModel = "gemini-3-pro-image";
        private int primaryMaxReferences = 4;
        private int fallbackMaxReferences = 5;
        private String aspectRatio = "3:4";
        private String imageSize = "1K";
        /** Generations 1..primaryGenerations use the primary model, later ones the fallback (D6). */
        private int primaryGenerations = 2;
        /** First generation plus 3 QA retries (spec: "retried up to 3 times"). */
        private int maxGenerations = 4;
        /** Longest side, in pixels, of images sent to Claude for visual QA. */
        private int qaMaxSidePx = 1024;
    }

    @Getter
    @Setter
    public static class LlmPrice {
        private BigDecimal inputPerMillionUsd;
        private BigDecimal outputPerMillionUsd;

        public LlmPrice() {
        }

        public LlmPrice(String input, String output) {
            this.inputPerMillionUsd = new BigDecimal(input);
            this.outputPerMillionUsd = new BigDecimal(output);
        }
    }

    @Getter
    @Setter
    public static class Pricing {
        private Map<String, LlmPrice> llm = new HashMap<>(Map.of(
                "claude-sonnet-5", new LlmPrice("2.00", "10.00"),
                "gpt-6-luna", new LlmPrice("0.10", "0.50")));
        private Map<String, BigDecimal> imagePerImageUsd = new HashMap<>(Map.of(
                "gemini-3.1-flash-image", new BigDecimal("0.067"),
                "gemini-3.1-flash-image-preview", new BigDecimal("0.067"),
                "gemini-3-pro-image", new BigDecimal("0.134"),
                "gemini-3-pro-image-preview", new BigDecimal("0.134")));
    }

    @Getter
    @Setter
    public static class Limits {
        private int dedicationMaxChars = 300;
        private int criticRewritesPerPage = 2;
        private int lookRegenerations = 2;
        private int pageRegenerationsPerBook = 3;
        private int draftsPerUserPerDay = 3;
        private BigDecimal maxBookCostUsd = new BigDecimal("3.00");
    }

    @Getter
    @Setter
    public static class Worker {
        private int concurrency = 4;
        private Duration pollDelay = Duration.ofSeconds(2);
        private Duration lease = Duration.ofMinutes(10);
        private Duration baseBackoff = Duration.ofSeconds(10);
        private int maxAttempts = 5;
    }
}
