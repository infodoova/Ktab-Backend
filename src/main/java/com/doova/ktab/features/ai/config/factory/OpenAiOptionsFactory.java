package com.doova.ktab.features.ai.config.factory;

import org.springframework.ai.openai.OpenAiChatOptions;

public class OpenAiOptionsFactory {

    /**
     * Determines whether a given OpenAI model supports custom temperature and top_p values.
     * Reasoning and next-generation models (e.g. o1, o3, gpt-6, luna) only support default temperature (1.0).
     */
    public static boolean supportsCustomTemperature(String model) {
        if (model == null) {
            return false;
        }
        String m = model.toLowerCase();
        return !(m.startsWith("o1") || m.startsWith("o3") || m.contains("luna") || m.startsWith("gpt-6") || m.contains("reasoning"));
    }

    /**
     * Checks if the model supports OpenAI reasoning effort parameter (o1, o3, gpt-6, luna).
     */
    public static boolean supportsReasoning(String model) {
        if (model == null) {
            return false;
        }
        String m = model.toLowerCase();
        return m.startsWith("o1") || m.startsWith("o3") || m.contains("luna") || m.startsWith("gpt-6") || m.contains("reasoning");
    }

    // ----------------------------------------------------------------------------------
    // 🏭 Factory Method: Centralizes the creation of OpenAiChatOptions.
    // ----------------------------------------------------------------------------------
    public static OpenAiChatOptions createOptions(String model, double temperature, double topP, Integer maxTokens, boolean streamUsage) {
        return createOptions(model, temperature, topP, maxTokens, streamUsage, "low", "fast");
    }

    public static OpenAiChatOptions createOptions(String model, double temperature, double topP, Integer maxTokens, boolean streamUsage, String reasoningEffort, String serviceTier) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model)
                .maxCompletionTokens(maxTokens)
                .streamUsage(streamUsage);

        if (supportsCustomTemperature(model)) {
            builder.temperature(temperature).topP(topP);
        }

        if (supportsReasoning(model) && reasoningEffort != null && !reasoningEffort.isBlank()) {
            builder.reasoningEffort(reasoningEffort);
        }

        if (serviceTier != null && !serviceTier.isBlank()) {
            builder.serviceTier(serviceTier);
        }

        return builder.build();
    }
}