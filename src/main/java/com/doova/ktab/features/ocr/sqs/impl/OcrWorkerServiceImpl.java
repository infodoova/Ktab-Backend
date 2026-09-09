package com.doova.ktab.features.ocr.sqs.impl;

import com.amazonaws.services.sqs.model.Message;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.features.ocr.ai.GeminiOcrService;
import com.doova.ktab.features.ocr.dto.GeminiOcrResponse;
import com.doova.ktab.features.ocr.quota.DynamicConcurrencyGate;
import com.doova.ktab.features.ocr.sqs.OcrPageMessage;
import com.doova.ktab.features.ocr.sqs.OcrQueueService;
import com.doova.ktab.features.ocr.sqs.OcrWorkerService;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "aws.sqs.ocr.worker-enabled", havingValue = "true")
public class OcrWorkerServiceImpl implements OcrWorkerService {

    private final OcrQueueService queueService;
    private final GeminiOcrService geminiService;
    private final DynamicConcurrencyGate concurrencyGate;
    private final BookPageRepository sectionRepository;
    private final BookRepository bookRepository;
    private final EntityManager entityManager;
    private final MeterRegistry meterRegistry;

    @Value("${ktab.ocr.retryLimit:3}")
    private int maxRetries;

    @Value("${ktab.circuitbreaker.failure-rate-threshold:50}")
    private int failureRateThreshold;

    @Value("${ktab.circuitbreaker.slow-call-duration-seconds:10}")
    private int slowCallDurationSeconds;

    @Value("${ktab.circuitbreaker.wait-duration-open-seconds:30}")
    private int waitDurationOpenSeconds;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService workerExecutor;
    private CircuitBreaker circuitBreaker;

    @Override
    @PostConstruct
    public void start() {
        if (!queueService.isEnabled()) {
            log.info("SQS OCR worker disabled - queue URL not configured");
            return;
        }

        // Initialize circuit breaker
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallDurationThreshold(Duration.ofSeconds(slowCallDurationSeconds))
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationOpenSeconds))
                .slidingWindowSize(20)
                .build();
        circuitBreaker = CircuitBreaker.of("sqs-ocr-worker", config);

        // Start worker threads
        int workerCount = Runtime.getRuntime().availableProcessors();
        workerExecutor = Executors.newFixedThreadPool(workerCount);
        running.set(true);

        for (int i = 0; i < workerCount; i++) {
            workerExecutor.submit(this::pollAndProcess);
        }

        log.info("Started {} SQS OCR worker threads", workerCount);
        meterRegistry.gauge("ocr.sqs.worker.threads", workerCount);
    }

    @Override
    @PreDestroy
    public void stop() {
        running.set(false);
        if (workerExecutor != null) {
            workerExecutor.shutdown();
            try {
                if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    workerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("SQS OCR worker stopped");
    }

    /**
     * Main polling loop for each worker thread.
     */
    private void pollAndProcess() {
        while (running.get()) {
            try {
                List<Message> messages = queueService.receiveMessages();

                for (Message message : messages) {
                    if (!running.get()) break;

                    processMessage(message);
                }

                // Brief pause if no messages
                if (messages.isEmpty()) {
                    Thread.sleep(1000);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in SQS polling loop", e);
                meterRegistry.counter("ocr.sqs.worker.poll.errors").increment();

                try {
                    Thread.sleep(5000); // Back off on errors
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Process a single SQS message.
     */
    private void processMessage(Message message) {
        Timer.Sample sample = Timer.start(meterRegistry);
        OcrPageMessage pageMessage = null;

        try {
            pageMessage = queueService.parseMessage(message);

            // Idempotency check
            if (sectionRepository.existsByBook_IdAndPageNumber(pageMessage.bookId(), pageMessage.pageNumber())) {
                log.debug("Page already processed, skipping: book={}, page={}",
                        pageMessage.bookId(), pageMessage.pageNumber());
                queueService.deleteMessage(message);
                meterRegistry.counter("ocr.sqs.worker.pages.skipped").increment();
                return;
            }

            // Process with circuit breaker
            OcrPageMessage finalPageMessage = pageMessage;
            circuitBreaker.executeRunnable(() -> processPage(finalPageMessage));

            // Success - delete message
            queueService.deleteMessage(message);
            sample.stop(meterRegistry.timer("ocr.sqs.worker.page.duration", "status", "success"));
            meterRegistry.counter("ocr.sqs.worker.pages.processed").increment();

        } catch (Exception e) {
            sample.stop(meterRegistry.timer("ocr.sqs.worker.page.duration", "status", "failure"));
            meterRegistry.counter("ocr.sqs.worker.pages.failed").increment();

            log.error("Failed to process OCR message: {}", e.getMessage());

            // Check retry count
            if (pageMessage != null && pageMessage.attemptCount() >= maxRetries) {
                queueService.moveToDeadLetter(pageMessage, e.getMessage());
                queueService.deleteMessage(message);
            }
            // Otherwise, message will become visible again after visibility timeout
        }
    }

    /**
     * Process a single page through OCR.
     */
    @Transactional
    protected void processPage(OcrPageMessage pageMessage) {
        try (var ignored = concurrencyGate.acquire()) {
            // Call Gemini OCR
            GeminiOcrResponse response = geminiService.ocrOnePageFromUrl(
                    pageMessage.presignedUrl(),
                    pageMessage.mime()
            );

            // Save result
            Book book = entityManager.getReference(Book.class, pageMessage.bookId());

            BookPage page = new BookPage();
            page.setBook(book);
            page.setPageNumber(pageMessage.pageNumber());
            page.setMarkdownContent(response.markdown());
            page.setWordCount(response.wordCount());
            page.setStatus(OcrStatus.COMPLETED);

            sectionRepository.save(page);

            log.debug("Processed page {} for book {} via SQS worker",
                    pageMessage.pageNumber(), pageMessage.bookId());
        }
    }
}
