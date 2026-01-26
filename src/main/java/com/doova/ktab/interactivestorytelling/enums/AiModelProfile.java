package com.doova.ktab.interactivestorytelling.enums;

import org.springframework.ai.openai.OpenAiChatOptions;

public enum AiModelProfile {

    STORY(OpenAiChatOptions.builder().model("gpt-4o").temperature(0.7).topP(0.9).maxTokens(700).build()),

    SUMMARY(OpenAiChatOptions.builder().model("gpt-4o").temperature(0.2).maxTokens(300).build());

    private final OpenAiChatOptions options;

    AiModelProfile(OpenAiChatOptions options) {
        this.options = options;
    }

    public OpenAiChatOptions options() {
        return options;
    }
}
