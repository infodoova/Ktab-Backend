package com.doova.ktab.event.listener;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.event.model.BookPublishedEvent;
import com.doova.ktab.features.ocr.sqs.OcrQueueService;
import com.doova.ktab.repository.book.BookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Event listener that triggers OCR processing when a book is published.
 * Supports both Spring Batch (single instance) and SQS-based (distributed) processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OcrEventListener {

    private final JobLauncher jobLauncher;
    private final Job ocrJob;
    private final JobExplorer jobExplorer;
    private final BookRepository bookRepository;
    private final OcrQueueService queueService;
    private final MeterRegistry meterRegistry;

    @Value("${aws.sqs.ocr.worker-enabled:false}")
    private boolean sqsWorkerEnabled;

    /**
     * Handle book published event - triggers OCR pipeline.
     * Runs asynchronously after the publishing transaction commits.
     */
    @Async("ocrTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookPublished(BookPublishedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Received BookPublishedEvent for bookId={}, pdfKey={}", event.bookId(), event.pdfKey());

        try {
            // Check if OCR job is already running for this book
            Optional<JobExecution> running = jobExplorer.findRunningJobExecutions("ocrJob")
                    .stream()
                    .filter(exec -> event.bookId().equals(exec.getJobParameters().getLong("bookId")))
                    .findFirst();

            if (running.isPresent()) {
                log.warn("OCR job already running for bookId={}, executionId={}", 
                        event.bookId(), running.get().getId());
                meterRegistry.counter("ocr.events.skipped", "reason", "already_running").increment();
                return;
            }

            // Update book status to PENDING
            bookRepository.updateOcrStatus(event.bookId(), OcrStatus.PENDING);

            // Launch OCR job
            JobParameters params = new JobParametersBuilder()
                    .addLong("bookId", event.bookId())
                    .addString("pdfKey", event.pdfKey())
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(ocrJob, params);
            
            log.info("Started OCR job for bookId={}, executionId={}, status={}", 
                    event.bookId(), execution.getId(), execution.getStatus());
            
            meterRegistry.counter("ocr.events.processed", "status", "success").increment();
            sample.stop(meterRegistry.timer("ocr.event.handling.duration", "status", "success"));

        } catch (Exception e) {
            log.error("Failed to start OCR job for bookId={}: {}", event.bookId(), e.getMessage(), e);
            
            // Update book OCR status to FAILED
            try {
                bookRepository.updateOcrStatus(event.bookId(), OcrStatus.FAILED);
            } catch (Exception updateEx) {
                log.error("Failed to update OCR status for bookId={}", event.bookId(), updateEx);
            }

            meterRegistry.counter("ocr.events.processed", "status", "failure").increment();
            sample.stop(meterRegistry.timer("ocr.event.handling.duration", "status", "failure"));
        }
    }
}
