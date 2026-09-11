package com.doova.ktab.features.ocr.service;

import com.doova.ktab.features.ocr.sqs.OcrPageMessage;

/**
 * Domain service for executing OCR page processing and persisting the result.
 */
public interface OcrPageProcessingService {

    /**
     * Processes an individual book page OCR task.
     * Checks idempotency, invokes AI OCR with concurrency throttling, and saves the page entity.
     *
     * @param pageMessage the validated OCR task payload
     * @return true if the page was processed and saved, false if skipped due to idempotency
     */
    boolean processPage(OcrPageMessage pageMessage);
}
