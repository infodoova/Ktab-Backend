package com.doova.ktab.features.imagegen.exception;

import lombok.Getter;

@Getter
public class ImageGenerationException extends RuntimeException {

    private final boolean retryable;

    public ImageGenerationException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public ImageGenerationException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }
}
