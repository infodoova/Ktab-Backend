package com.doova.ktab.features.storybook.llm;

import java.util.List;

/**
 * An LLM answer that knows whether it is usable. The router checks it once for every call, on every provider, so an
 * empty or half-read answer is retried and never stored or passed to the next step.
 */
public interface ValidatedLlmResponse {

    /** What makes this answer unusable; empty when it can be used. */
    List<String> problems();
}
