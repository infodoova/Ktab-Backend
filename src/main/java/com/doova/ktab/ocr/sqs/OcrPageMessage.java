package com.doova.ktab.ocr.sqs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SQS message representing a single page to be OCR processed.
 * Used for distributed processing across multiple worker instances.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrPageMessage(
        Long bookId,
        int pageNumber,
        String s3Key,
        String mime,
        String presignedUrl,
        long timestamp,
        int attemptCount
) {
    public OcrPageMessage withIncrementedAttempt() {
        return new OcrPageMessage(bookId, pageNumber, s3Key, mime, presignedUrl, timestamp, attemptCount + 1);
    }

    public static OcrPageMessage create(Long bookId, int pageNumber, String s3Key, String mime, String presignedUrl) {
        return new OcrPageMessage(bookId, pageNumber, s3Key, mime, presignedUrl, System.currentTimeMillis(), 0);
    }
}
