package com.doova.ktab.features.story.enums;

import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Pre-built OpenAI option sets for story feature.
 * Model names are intentionally left as placeholders here;
 * OpenAIConfig overrides them at bean-creation time using GlobalAiProperties.
 * Do NOT change model strings here — change ktab.ai.text.primary instead.
 */
public enum AiModelProfile {

    STORY("low", 8192, 0.7, 0.9),
    SUMMARY("none", 4096, 0.2, 1.0);

    private final String reasoningEffort;
    private final int maxTokens;
    private final double defaultTemp;
    private final double defaultTopP;
    private OpenAiChatOptions options;

    AiModelProfile(String reasoningEffort, int maxTokens, double defaultTemp, double defaultTopP) {
        this.reasoningEffort = reasoningEffort;
        this.maxTokens = maxTokens;
        this.defaultTemp = defaultTemp;
        this.defaultTopP = defaultTopP;
        this.options = com.doova.ktab.features.ai.config.factory.OpenAiOptionsFactory.createOptions(
                "gpt-6-luna", defaultTemp, defaultTopP, maxTokens, false, reasoningEffort, "fast");
    }

    public OpenAiChatOptions options() {
        return options;
    }

    /** Called by OpenAIConfig at startup to override the model from GlobalAiProperties. */
    public void overrideModel(String modelName) {
        this.options = com.doova.ktab.features.ai.config.factory.OpenAiOptionsFactory.createOptions(
                modelName, defaultTemp, defaultTopP, maxTokens, false, reasoningEffort, "fast");
    }
}
