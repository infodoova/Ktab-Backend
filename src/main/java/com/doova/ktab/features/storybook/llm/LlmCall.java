package com.doova.ktab.features.storybook.llm;

public record LlmCall<T>(T value, String model, long inputTokens, long outputTokens, long latencyMs) {
}
