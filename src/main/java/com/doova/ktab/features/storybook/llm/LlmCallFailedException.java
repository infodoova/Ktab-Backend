package com.doova.ktab.features.storybook.llm;

public class LlmCallFailedException extends RuntimeException {

    private final boolean retryable;

    public LlmCallFailedException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
