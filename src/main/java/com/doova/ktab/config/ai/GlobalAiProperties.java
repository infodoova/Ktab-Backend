package com.doova.ktab.config.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Central AI model configuration for the entire application.
 *
 * All features read their model names from here instead of hard-coding them or
 * duplicating per-feature properties.  Changing a model globally is a single
 * env-var or property change:
 *
 *   ktab.ai.text.primary=gpt-6-luna          # or set KTAB_AI_TEXT_PRIMARY
 *   ktab.ai.image.primary=gemini-3.1-flash-image  # or set KTAB_AI_IMAGE_PRIMARY
 */
@Configuration
@ConfigurationProperties(prefix = "ktab.ai")
@Getter
@Setter
public class GlobalAiProperties {

    private Text text = new Text();
    private Image image = new Image();

    @Getter
    @Setter
    public static class Text {
        /** Primary LLM for all OpenAI text generation across every feature. */
        private String primary = "gpt-6-luna";

        /** Fallback model used when the primary is unavailable (rate-limit / API error). Lower model fallback. */
        private String fallback = "gpt-4o";

        /** Embedding model for vector lookups. */
        private String embedding = "text-embedding-3-small";

        /** Model used for live Google web search synthesis / grounding. */
        private String search = "gemini-2.5-flash";

        /** Default reasoning effort for reasoning models ('none', 'low', 'medium', 'high'). */
        private String reasoningEffort = "low";

        /** Service tier for OpenAI requests ('fast', 'auto', or 'default'). */
        private String serviceTier = "fast";
    }

    @Getter
    @Setter
    public static class Image {
        /** Primary Gemini image model used across all image-generation features (best Flash version). */
        private String primary = "gemini-3.1-flash-image";

        /** Fallback model tried automatically when the primary fails (3.1 Lite lower tier). */
        private String fallback = "gemini-3.1-flash-lite-image";

        /** Image resolution/size across features (e.g. 1K or 2K). Default is 1K. */
        private String imageSize = "1K";
    }
}
