package com.doova.ktab.features.ocr.service.impl;

import com.doova.ktab.config.qstash.QStashSignatureVerifier;
import com.doova.ktab.features.ocr.dto.OcrCallbackResult;
import com.doova.ktab.features.ocr.service.OcrCallbackService;
import com.doova.ktab.features.ocr.service.OcrPageProcessingService;
import com.doova.ktab.features.ocr.sqs.OcrPageMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of OCR callback orchestration.
 * Handles webhook security verification, payload deserialization, telemetry metrics,
 * and delegates domain processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OcrCallbackServiceImpl implements OcrCallbackService {

    private final QStashSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final OcrPageProcessingService pageProcessingService;
    private final MeterRegistry meterRegistry;

    @Override
    public OcrCallbackResult processCallback(String signature, String rawBody) {
        Timer.Sample sample = Timer.start(meterRegistry);

        // 1. Signature Verification
        if (!signatureVerifier.verify(signature, rawBody)) {
            log.warn("Unauthorized OCR callback received - signature check failed");
            meterRegistry.counter("ocr.qstash.unauthorized").increment();
            return OcrCallbackResult.INVALID_SIGNATURE;
        }

        // 2. Payload Parsing
        OcrPageMessage pageMessage;
        try {
            pageMessage = objectMapper.readValue(rawBody, OcrPageMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse OCR message body: {}", e.getMessage());
            meterRegistry.counter("ocr.qstash.parse.errors").increment();
            return OcrCallbackResult.INVALID_PAYLOAD;
        }

        // 3. Domain Processing & Metrics
        try {
            boolean processed = pageProcessingService.processPage(pageMessage);

            if (!processed) {
                meterRegistry.counter("ocr.qstash.pages.skipped").increment();
                return OcrCallbackResult.SKIPPED;
            }

            meterRegistry.counter("ocr.qstash.pages.processed").increment();
            sample.stop(meterRegistry.timer("ocr.qstash.page.duration", "status", "success"));
            return OcrCallbackResult.SUCCESS;

        } catch (Exception e) {
            log.error("Failed to process OCR page {} for book {}: {}",
                    pageMessage.pageNumber(), pageMessage.bookId(), e.getMessage(), e);
            meterRegistry.counter("ocr.qstash.pages.failed").increment();
            sample.stop(meterRegistry.timer("ocr.qstash.page.duration", "status", "failure"));
            return OcrCallbackResult.PROCESSING_FAILED;
        }
    }
}
