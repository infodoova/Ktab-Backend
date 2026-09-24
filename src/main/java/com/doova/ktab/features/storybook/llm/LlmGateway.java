package com.doova.ktab.features.storybook.llm;

/** The only way storybook code talks to an LLM. Tests use {@code FakeLlmGateway}. */
public interface LlmGateway {
    <T> LlmCall<T> call(LlmRequest<T> request);
}
