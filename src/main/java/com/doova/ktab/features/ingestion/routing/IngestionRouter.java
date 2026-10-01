package com.doova.ktab.features.ingestion.routing;

import com.doova.ktab.enums.book.IngestionRoute;
import com.doova.ktab.enums.book.PdfType;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.ingestion.config.IngestionProperties;
import com.doova.ktab.features.ingestion.config.OcrSwitchProperties;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.ingestion.pdf.PdfClassificationResult;
import com.doova.ktab.features.ingestion.pdf.PdfTypeClassifier;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Optional;

/**
 * Classifies a book's PDF and hands it to exactly one of the two independent ingestion
 * pipelines. This class — not a Spring Batch flow — is the branch point: {@code ocrJob}
 * is unmodified by v3, and there is no {@code FlowBuilder}/{@code .on(...)} branch inside
 * any job definition. See docs/ocr_engine_v3.md, "Two independent pipelines" and Phase 2.
 * <p>
 * The STUDIO route is not wired to a job yet (Phase 3/4 of the plan) — see {@link #launch}.
 * Until then, {@code shadowMode} (the default) means every book resolves to OCR regardless
 * of classifier output, so that branch is unreachable in default configuration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionRouter {

    /** Bumped whenever classifier thresholds/logic change meaningfully, so a corpus can be
     *  identified and re-classified later. See docs/ocr_engine_v3.md, Phase 0. */
    public static final String CLASSIFIER_VERSION = "v1";

    private final PdfTypeClassifier classifier;
    private final BookContentPurger purger;
    private final BookRepository bookRepository;
    private final ObjectStorageService storage;
    private final ObjectMapper objectMapper;
    private final IngestionProperties properties;
    private final MeterRegistry meterRegistry;

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    @Qualifier("ocrJob")
    private final Job ocrJob;
    @Qualifier("studioIngestionJob")
    private final Job studioIngestionJob;
    @Qualifier("nativeIngestionJob")
    private final Job nativeIngestionJob;
    private final StudioProperties studioProperties;
    private final OcrSwitchProperties ocrSwitch;

    /**
     * Full flow for a fresh ingestion: classify, persist the verdict, resolve (and persist)
     * the route, purge any existing content, and launch the resolved pipeline.
     *
     * @return the route that was launched
     */
    public IngestionRoute ingest(Long bookId, String pdfKey) throws Exception {
        Optional<JobExecution> alreadyRunning = findRunning(bookId);
        if (alreadyRunning.isPresent()) {
            log.warn("Ingestion already running for bookId={}, executionId={}",
                    bookId, alreadyRunning.get().getId());
            meterRegistry.counter("ingestion.route.skipped", "reason", "already_running").increment();
            return null;
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        PdfClassificationResult result = classify(book, pdfKey);
        IngestionRoute route = persistClassificationAndResolveRoute(book, result);

        meterRegistry.counter("ingestion.route.selected", "route", route.name()).increment();

        purger.purge(bookId);

        launch(bookId, pdfKey, route);
        return route;
    }

    /**
     * Escape hatch: re-run classification only. Persists the verdict and (if the route isn't
     * admin-locked) re-resolves the route, but never purges content or launches a job.
     * See docs/ocr_engine_v3.md, Phase 2.4.
     */
    public PdfClassificationResult classifyOnly(Long bookId, String pdfKey) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        PdfClassificationResult result = classify(book, pdfKey);
        persistClassificationAndResolveRoute(book, result);
        return result;
    }

    /**
     * Escape hatch: admin override. Pins the route and locks it so future (re-)classification
     * runs never overwrite it. See docs/ocr_engine_v3.md, Phase 2.4.
     */
    public void overrideRoute(Long bookId, IngestionRoute route) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        book.setIngestionRoute(route);
        book.setIngestionRouteLocked(true);
        bookRepository.save(book);

        meterRegistry.counter("ingestion.route.overridden", "to", route.name()).increment();
        log.info("Admin override: bookId={} pinned to route={}", bookId, route);
    }

    private PdfClassificationResult classify(Book book, String pdfKey) {
        IngestionProperties.Classification cfg = properties.getClassification();

        if (!cfg.isEnabled()) {
            return PdfClassificationResult.builder()
                    .pdfType(PdfType.UNKNOWN)
                    .reason("classification disabled")
                    .build();
        }

        long started = System.currentTimeMillis();
        try (InputStream pdfStream = storage.getStream(pdfKey)) {
            PdfClassificationResult result = classifier.classify(pdfStream, book.getLanguage());
            meterRegistry.counter("ingestion.classification.result", "type", result.getPdfType().name()).increment();
            meterRegistry.timer("ingestion.classification.duration")
                    .record(java.time.Duration.ofMillis(System.currentTimeMillis() - started));
            return result;
        } catch (Exception e) {
            log.warn("Classification failed for bookId={}: {}", book.getId(), e.getMessage(), e);
            return PdfClassificationResult.unknown("exception: " + e.getMessage(), 0);
        }
    }

    private IngestionRoute persistClassificationAndResolveRoute(Book book, PdfClassificationResult result) {
        PdfType previousType = book.getPdfType();

        book.setPdfType(result.getPdfType());
        book.setClassifierVersion(CLASSIFIER_VERSION);
        try {
            book.setPdfClassification(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            log.warn("Failed to serialize classification evidence for bookId={}: {}", book.getId(), e.getMessage());
        }

        IngestionRoute route = resolveRoute(book, result);
        book.setIngestionRoute(route);
        bookRepository.save(book);

        if (previousType != null && previousType != result.getPdfType()) {
            log.info("bookId={} reclassified {} -> {}", book.getId(), previousType, result.getPdfType());
        }
        return route;
    }

    private IngestionRoute resolveRoute(Book book, PdfClassificationResult result) {
        return RouteResolver.resolve(result.getPdfType(), properties.getClassification(), studioProperties.isEnabled(),
                ocrSwitch.isEnabled(), book.getIngestionRoute(), book.isIngestionRouteLocked());
    }

    private void launch(Long bookId, String pdfKey, IngestionRoute route) throws Exception {
        switch (route) {
            case OCR -> launchOcr(bookId, pdfKey);
            case STUDIO -> launchStudio(bookId, pdfKey);
            case NATIVE -> launchNative(bookId, pdfKey);
        }
    }

    private JobExecution launchStudio(Long bookId, String pdfKey) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("bookId", bookId)
                .addString("pdfKey", pdfKey)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(studioIngestionJob, params);
        log.info("Started Studio ingestion batch job for bookId={}, executionId={}, status={}",
                bookId, execution.getId(), execution.getStatus());
        return execution;
    }

    private JobExecution launchNative(Long bookId, String pdfKey) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("bookId", bookId)
                .addString("pdfKey", pdfKey)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(nativeIngestionJob, params);
        log.info("Started native ingestion batch job for bookId={}, executionId={}, status={}",
                bookId, execution.getId(), execution.getStatus());
        return execution;
    }

    private JobExecution launchOcr(Long bookId, String pdfKey) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("bookId", bookId)
                .addString("pdfKey", pdfKey)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(ocrJob, params);
        log.info("Started OCR batch job for bookId={}, executionId={}, status={}",
                bookId, execution.getId(), execution.getStatus());
        return execution;
    }

    private Optional<JobExecution> findRunning(Long bookId) {
        return jobExplorer.findRunningJobExecutions("ocrJob").stream()
                .filter(exec -> bookId.equals(exec.getJobParameters().getLong("bookId")))
                .findFirst()
                .or(() -> jobExplorer.findRunningJobExecutions("studioIngestionJob").stream()
                        .filter(exec -> bookId.equals(exec.getJobParameters().getLong("bookId")))
                        .findFirst())
                .or(() -> jobExplorer.findRunningJobExecutions("nativeIngestionJob").stream()
                        .filter(exec -> bookId.equals(exec.getJobParameters().getLong("bookId")))
                        .findFirst());
    }
}
