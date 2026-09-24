package com.doova.ktab.features.studio.client;

/**
 * Base type for ElevenLabs Studio API failures. Split into retryable vs fatal so
 * {@link ElevenLabsStudioClient}'s retry loop knows which one it's holding without
 * re-inspecting the HTTP status. See docs/ocr_engine_v3.md, Phase 4.4.
 */
public abstract class StudioApiException extends RuntimeException {

    private final int statusCode;

    protected StudioApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    protected StudioApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** -1 for a transport-level failure with no HTTP response at all. */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Network failures, HTTP 429, and HTTP 5xx — retry with exponential backoff and full
     * jitter, honoring {@code Retry-After} when present.
     */
    public static class Retryable extends StudioApiException {
        public Retryable(String message, int statusCode) {
            super(message, statusCode);
        }

        public Retryable(String message, int statusCode, Throwable cause) {
            super(message, statusCode, cause);
        }
    }

    /**
     * Any other 4xx — invalid authentication, bad request, not found. Fails immediately;
     * retrying it burns quota for a call that will never succeed.
     */
    public static final class Fatal extends StudioApiException {
        public Fatal(String message, int statusCode) {
            super(message, statusCode);
        }
    }
}
