package com.doova.ktab.features.ocr.sqs.impl;

import com.doova.ktab.config.qstash.QStashClient;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.features.ocr.sqs.OcrPageMessage;
import com.doova.ktab.features.ocr.sqs.OcrQueueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrQueueServiceImpl implements OcrQueueService {

    private final QStashClient qStashClient;
    private final ObjectMapper objectMapper;
    private final S3OcrStorageService s3Storage;
    private final MeterRegistry meterRegistry;

    @Override
    public int publishBookPages(Long bookId) {
        if (!isEnabled()) {
            log.warn("QStash queue not configured, skipping queue publish for book {}", bookId);
            return 0;
        }

        List<String> pageKeys = s3Storage.listPageKeys(bookId);
        int published = 0;

        for (int i = 0; i < pageKeys.size(); i++) {
            String key = pageKeys.get(i);
            int pageNumber = i + 1;
            String mime = resolveMime(key);
            String presignedUrl = s3Storage.generatePresignedUrl(key);

            OcrPageMessage message = OcrPageMessage.create(bookId, pageNumber, key, mime, presignedUrl);

            try {
                publishMessage(message);
                published++;
            } catch (Exception e) {
                log.error("Failed to publish page {} for book {} to QStash", pageNumber, bookId, e);
                meterRegistry.counter("ocr.qstash.publish.errors").increment();
            }
        }

        log.info("Published {} pages for book {} to QStash queue", published, bookId);
        meterRegistry.counter("ocr.qstash.pages.published").increment(published);
        return published;
    }

    @Override
    public void publishMessage(OcrPageMessage message) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            String deduplicationId = "book-" + message.bookId() + "-page-" + message.pageNumber() + "-" + UUID.randomUUID();
            qStashClient.publishMessage(messageBody, deduplicationId);
            meterRegistry.counter("ocr.qstash.messages.sent").increment();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OCR message", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return qStashClient.isConfigured();
    }

    private String resolveMime(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "image/png";
    }
}

