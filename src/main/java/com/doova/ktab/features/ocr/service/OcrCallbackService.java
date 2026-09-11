package com.doova.ktab.features.ocr.service;

import com.doova.ktab.features.ocr.dto.OcrCallbackResult;

/**
 * Service for orchestrating inbound QStash OCR webhooks.
 * Responsible for signature verification, payload deserialization, metrics collection,
 * and dispatching to page processing.
 */
public interface OcrCallbackService {

    /**
     * Orchestrates the verification, parsing, and processing of an inbound OCR callback.
     *
     * @param signature the Upstash-Signature header
     * @param rawBody   the raw HTTP request body
     * @return outcome status indicating success, skipped, unauthorized, bad request, or failure
     */
    OcrCallbackResult processCallback(String signature, String rawBody);
}
