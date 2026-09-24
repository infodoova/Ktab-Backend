package com.doova.ktab.features.storybook.image;

public class ImageGenerationException extends RuntimeException {

    private final boolean retryable;

    public ImageGenerationException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
