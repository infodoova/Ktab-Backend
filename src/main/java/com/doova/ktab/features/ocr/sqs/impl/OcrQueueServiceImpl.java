package com.doova.ktab.features.ocr.sqs.impl;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.*;
import com.doova.ktab.features.ocr.sqs.OcrPageMessage;
import com.doova.ktab.features.ocr.sqs.OcrQueueService;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrQueueServiceImpl implements OcrQueueService {

    private final AmazonSQS sqs;
    private final ObjectMapper objectMapper;
    private final S3OcrStorageService s3Storage;
    private final MeterRegistry meterRegistry;

    @Value("${aws.sqs.ocr.queue-url:}")
    private String queueUrl;

    @Value("${aws.sqs.ocr.dead-letter-queue-url:}")
    private String dlqUrl;

    @Value("${aws.sqs.ocr.visibility-timeout-seconds:300}")
    private int visibilityTimeout;

    @Value("${aws.sqs.ocr.max-messages-per-poll:10}")
    private int maxMessagesPerPoll;

    @Override
    public int publishBookPages(Long bookId) {
        if (queueUrl == null || queueUrl.isBlank()) {
            log.warn("SQS queue URL not configured, skipping queue publish for book {}", bookId);
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
                log.error("Failed to publish page {} for book {} to SQS", pageNumber, bookId, e);
                meterRegistry.counter("ocr.sqs.publish.errors").increment();
            }
        }

        log.info("Published {} pages for book {} to SQS queue", published, bookId);
        meterRegistry.counter("ocr.sqs.pages.published").increment(published);
        return published;
    }

    @Override
    public void publishMessage(OcrPageMessage message) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);

            SendMessageRequest request = new SendMessageRequest()
                    .withQueueUrl(queueUrl)
                    .withMessageBody(messageBody)
                    .withMessageGroupId("book-" + message.bookId()) // FIFO ordering by book
                    .withMessageDeduplicationId(UUID.randomUUID().toString());

            sqs.sendMessage(request);
            meterRegistry.counter("ocr.sqs.messages.sent").increment();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OCR message", e);
        }
    }

    @Override
    public List<Message> receiveMessages() {
        if (queueUrl == null || queueUrl.isBlank()) {
            return List.of();
        }

        ReceiveMessageRequest request = new ReceiveMessageRequest()
                .withQueueUrl(queueUrl)
                .withMaxNumberOfMessages(maxMessagesPerPoll)
                .withVisibilityTimeout(visibilityTimeout)
                .withWaitTimeSeconds(20); // Long polling

        ReceiveMessageResult result = sqs.receiveMessage(request);
        meterRegistry.counter("ocr.sqs.messages.received").increment(result.getMessages().size());

        return result.getMessages();
    }

    @Override
    public OcrPageMessage parseMessage(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), OcrPageMessage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse OCR message", e);
        }
    }

    @Override
    public void deleteMessage(Message message) {
        sqs.deleteMessage(new DeleteMessageRequest()
                .withQueueUrl(queueUrl)
                .withReceiptHandle(message.getReceiptHandle()));
        meterRegistry.counter("ocr.sqs.messages.deleted").increment();
    }

    @Override
    public void moveToDeadLetter(OcrPageMessage message, String errorReason) {
        if (dlqUrl == null || dlqUrl.isBlank()) {
            log.warn("DLQ URL not configured, cannot move failed message");
            return;
        }

        try {
            String messageBody = objectMapper.writeValueAsString(message);

            SendMessageRequest request = new SendMessageRequest()
                    .withQueueUrl(dlqUrl)
                    .withMessageBody(messageBody)
                    .withMessageAttributes(java.util.Map.of(
                            "errorReason", new MessageAttributeValue()
                                    .withDataType("String")
                                    .withStringValue(truncate(errorReason, 256))
                    ));

            sqs.sendMessage(request);
            meterRegistry.counter("ocr.sqs.messages.dlq").increment();
            log.warn("Moved failed message to DLQ: book={}, page={}", message.bookId(), message.pageNumber());

        } catch (JsonProcessingException e) {
            log.error("Failed to move message to DLQ", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return queueUrl != null && !queueUrl.isBlank();
    }

    private String resolveMime(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "image/png";
    }

    private String truncate(String s, int maxLength) {
        return s != null && s.length() > maxLength ? s.substring(0, maxLength) : s;
    }
}
