package com.doova.ktab.features.ocr.dto;

/**
 * Represents the outcome of processing an inbound QStash OCR callback.
 */
public enum OcrCallbackResult {
    /**
     * Page was successfully OCR processed and persisted.
     */
    SUCCESS,

    /**
     * Page was already processed previously and was skipped (idempotent).
     */
    SKIPPED,

    /**
     * Webhook signature was missing or invalid.
     */
    INVALID_SIGNATURE,

    /**
     * Webhook request body could not be parsed into a valid OCR page message.
     */
    INVALID_PAYLOAD,

    /**
     * Internal processing error occurred (Gemini failure, DB error, etc.).
     * Triggers HTTP 5xx so QStash will retry according to configured backoff.
     */
    PROCESSING_FAILED
}
