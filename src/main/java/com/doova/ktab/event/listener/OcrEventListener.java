package com.doova.ktab.event.listener;

import com.doova.ktab.enums.book.IngestionRoute;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.event.model.BookPublishedEvent;
import com.doova.ktab.features.ingestion.config.OcrSwitchProperties;
import com.doova.ktab.features.ingestion.routing.IngestionRouter;
import com.doova.ktab.features.ocr.sqs.OcrQueueService;
import com.doova.ktab.repository.book.BookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener that triggers ingestion when a book is published.
 * Supports both Spring Batch (single instance, via {@link IngestionRouter}) and
 * SQS-based (distributed) processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OcrEventListener {

    private final IngestionRouter ingestionRouter;
    private final BookRepository bookRepository;
    private final OcrQueueService queueService;
    private final MeterRegistry meterRegistry;
    private final OcrSwitchProperties ocrSwitch;

    @Value("${qstash.ocr.worker-enabled:${aws.sqs.ocr.worker-enabled:false}}")
    private boolean queueWorkerEnabled;

    /**
     * Handle book published event - triggers ingestion.
     * Runs asynchronously after the publishing transaction commits.
     * Includes fallbackExecution = true to prevent silent event drop if published outside a transaction.
     */
    @Async("ocrTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleBookPublished(BookPublishedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Received BookPublishedEvent for bookId={}, pdfKey={}", event.bookId(), event.pdfKey());

        try {
            // Update book status to PENDING
            bookRepository.updateOcrStatus(event.bookId(), OcrStatus.PENDING);

            // The queue path sends every page straight to OCR, so it is closed while OCR is off.
            if (ocrSwitch.isEnabled() && queueWorkerEnabled && queueService != null && queueService.isEnabled()) {
                // NOTE: the distributed queue path bypasses classification entirely and always
                // goes straight to OCR page-by-page dispatch. Wiring classification into
                // OcrQueueService is future work — see docs/ocr_engine_v3.md, Phase 2.
                log.info("Dispatching OCR pages via queue for bookId={}", event.bookId());
                int queuedPages = queueService.publishBookPages(event.bookId());
                log.info("Queued {} pages for OCR processing via queue for bookId={}", queuedPages, event.bookId());
            } else {
                // Classify, resolve route, purge, and launch. IngestionRouter owns the
                // already-running check for both ocrJob and studioIngestionJob.
                IngestionRoute route = ingestionRouter.ingest(event.bookId(), event.pdfKey());

                if (route == null) {
                    log.warn("Ingestion already running for bookId={}, skipping", event.bookId());
                    meterRegistry.counter("ocr.events.skipped", "reason", "already_running").increment();
                    return;
                }

                log.info("Ingestion started for bookId={} via route={}", event.bookId(), route);
            }

            meterRegistry.counter("ocr.events.processed", "status", "success").increment();
            sample.stop(meterRegistry.timer("ocr.event.handling.duration", "status", "success"));

        } catch (Exception e) {
            log.error("Failed to start OCR processing for bookId={}: {}", event.bookId(), e.getMessage(), e);

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
