package com.doova.ktab.ai.config.factory;

import org.springframework.ai.openai.OpenAiChatOptions;

public class OpenAiOptionsFactory {
    // ----------------------------------------------------------------------------------
    // 🏭 Factory Method: Centralizes the creation of OpenAiChatOptions.
    // ----------------------------------------------------------------------------------
    public static OpenAiChatOptions createOptions(
            String model,
            double temperature,
            double topP,
            Integer maxTokens,
            boolean streamUsage) {

        // 💡 OOP: Encapsulation - All option-building logic is hidden here.
        return OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .streamUsage(streamUsage)
                .build();
    }
}