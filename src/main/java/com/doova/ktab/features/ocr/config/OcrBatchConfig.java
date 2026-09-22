package com.doova.ktab.features.ocr.config;

import com.doova.ktab.features.ocr.ai.GeminiOcrService;
import com.doova.ktab.features.ocr.batch.*;
import com.doova.ktab.features.ocr.harmonize.HarmonizationService;
import com.doova.ktab.features.ocr.harmonize.HarmonizationTasklet;
import com.doova.ktab.features.ocr.harmonize.PageStitcher;
import com.doova.ktab.features.ocr.image.*;
import com.doova.ktab.features.ocr.quality.QualityGateTasklet;
import com.doova.ktab.features.ocr.quota.DynamicConcurrencyGate;
import com.doova.ktab.features.ocr.repository.OcrFailureRepository;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.features.ocr.structure.*;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.concurrent.Future;

@Configuration
@RequiredArgsConstructor
public class OcrBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager tx;
    private final S3OcrStorageService s3;
    private final GeminiOcrService gemini;
    private final DynamicConcurrencyGate gate;
    private final BookPageRepository pageRepository;
    private final BookSectionRepository sectionRepository;
    private final BookRepository bookRepository;
    private final OcrFailureRepository failures;
    private final MeterRegistry meterRegistry;
    private final OcrProperties properties;

    // Image Preparation beans
    private final PageRenderer pageRenderer;
    private final BorderCropper borderCropper;
    private final SpreadDetector spreadDetector;
    private final SpreadSplitter spreadSplitter;
    private final OrientationPreChecker orientationPreChecker;
    private final ImageQualityAnalyzer imageQualityAnalyzer;

    // Repetition & Prompts
    private final RepetitionDetector repetitionDetector;

    // TOC sources
    private final List<TocSource> tocSources;

    // Structure Resolution beans
    private final PaginationModeDetector paginationModeDetector;
    private final TocAligner tocAligner;
    private final HeadingsStructureBuilder headingsStructureBuilder;
    private final SectionTreeBuilder sectionTreeBuilder;

    // Harmonization beans
    private final HarmonizationService harmonizationService;
    private final PageStitcher pageStitcher;

    // Circuit breaker settings
    @Value("${ktab.circuitbreaker.failure-rate-threshold:50}")
    private int failureRateThreshold;

    @Value("${ktab.circuitbreaker.slow-call-rate-threshold:80}")
    private int slowCallRateThreshold;

    @Value("${ktab.circuitbreaker.slow-call-duration-seconds:10}")
    private int slowCallDurationSeconds;

    @Value("${ktab.circuitbreaker.wait-duration-open-seconds:30}")
    private int waitDurationOpenSeconds;

    @Value("${ktab.circuitbreaker.sliding-window-size:20}")
    private int slidingWindowSize;

    // =========================
    // Infrastructure
    // =========================

    @Bean
    public TaskExecutor ocrTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getParallelism());
        executor.setMaxPoolSize(properties.getParallelism() * 2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ocr-v2-thread-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        meterRegistry.gauge("ocr.threadpool.active", executor, ThreadPoolTaskExecutor::getActiveCount);
        meterRegistry.gauge("ocr.threadpool.pool_size", executor, ThreadPoolTaskExecutor::getPoolSize);

        return executor;
    }

    // =========================
    // Jobs
    // =========================

    @Bean
    public Job ocrJob(
            Step decompositionStep,
            Step ocrStep,
            Step tocStep,
            Step structureStep,
            Step harmonizeStep,
            Step qualityStep
    ) {
        return new JobBuilder("ocrJob", jobRepository)
                .listener(new OcrCleanUpListener(bookRepository, s3))
                .start(decompositionStep)
                .next(ocrStep)
                .next(tocStep)
                .next(structureStep)
                .next(harmonizeStep)
                .next(qualityStep)
                .listener(new OcrCleanUpListener(bookRepository, s3))
                .build();
    }

    @Bean
    public Job restructureJob(Step tocStep, Step structureStep, Step qualityStep) {
        return new JobBuilder("restructureJob", jobRepository)
                .start(tocStep)
                .next(structureStep)
                .next(qualityStep)
                .build();
    }

    @Bean
    public Job harmonizeJob(Step harmonizeStep, Step qualityStep) {
        return new JobBuilder("harmonizeJob", jobRepository)
                .start(harmonizeStep)
                .next(qualityStep)
                .build();
    }

    // =========================
    // Step 1: Decomposition & Image Preparation
    // =========================

    @Bean
    public Step decompositionStep() {
        return new StepBuilder("decompositionStep", jobRepository)
                .tasklet(pageImagePreparationTasklet(null, null), tx)
                .build();
    }

    @Bean
    @StepScope
    public PageImagePreparationTasklet pageImagePreparationTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId,
            @Value("#{jobParameters['pdfKey']}") String pdfKey
    ) {
        return new PageImagePreparationTasklet(
                pageRenderer,
                borderCropper,
                spreadDetector,
                spreadSplitter,
                orientationPreChecker,
                imageQualityAnalyzer,
                s3,
                bookRepository,
                pageRepository,
                bookId,
                pdfKey,
                meterRegistry
        );
    }

    // =========================
    // Step 2: Page OCR
    // =========================

    @Bean
    public Step ocrStep(
            AsyncItemProcessor<PageItem, OcrResult> asyncProcessor,
            AsyncItemWriter<OcrResult> asyncWriter,
            org.springframework.batch.item.ItemStreamReader<PageItem> pageReader
    ) {
        return new StepBuilder("ocrStep", jobRepository)
                .<PageItem, Future<OcrResult>>chunk(properties.getChunkSize(), tx)
                .reader(pageReader)
                .stream(pageReader)
                .processor(asyncProcessor)
                .writer(asyncWriter)
                .faultTolerant()
                .retryLimit(properties.getRetryLimit())
                .retry(Exception.class)
                .skip(Exception.class)
                .skipLimit(Integer.MAX_VALUE)
                .listener(new OcrSkipListener(s3, failures, bookRepository, properties.getRetryLimit()))
                .build();
    }

    @Bean
    public AsyncItemProcessor<PageItem, OcrResult> asyncProcessor(GeminiOcrProcessor processor) {
        AsyncItemProcessor<PageItem, OcrResult> asyncProcessor = new AsyncItemProcessor<>();
        asyncProcessor.setDelegate(processor);
        asyncProcessor.setTaskExecutor(ocrTaskExecutor());
        return asyncProcessor;
    }

    @Bean
    public AsyncItemWriter<OcrResult> asyncWriter(BookPageWriter writer) {
        AsyncItemWriter<OcrResult> asyncWriter = new AsyncItemWriter<>();
        asyncWriter.setDelegate(writer);
        return asyncWriter;
    }

    @Bean
    public GeminiOcrProcessor ocrProcessor() {
        return new GeminiOcrProcessor(
                gemini,
                gate,
                repetitionDetector,
                properties.getPromptVersion(),
                properties.getRepetition().getMaxRepeatedLines(),
                meterRegistry,
                failureRateThreshold,
                slowCallRateThreshold,
                slowCallDurationSeconds,
                waitDurationOpenSeconds,
                slidingWindowSize
        );
    }

    @Bean
    @StepScope
    public org.springframework.batch.item.ItemStreamReader<PageItem> pageReader(@Value("#{jobParameters['bookId']}") Long bookId) {
        return new BookPageManifestReader(bookId, pageRepository, s3, meterRegistry);
    }

    // =========================
    // Step 3: TOC Extraction
    // =========================

    @Bean
    public Step tocStep() {
        return new StepBuilder("tocStep", jobRepository)
                .tasklet(tocExtractionTasklet(null), tx)
                .build();
    }

    @Bean
    @StepScope
    public TocExtractionTasklet tocExtractionTasklet(@Value("#{jobParameters['bookId']}") Long bookId) {
        return new TocExtractionTasklet(bookId, tocSources, bookRepository, meterRegistry);
    }

    // =========================
    // Step 4: Structure Resolution
    // =========================

    @Bean
    public Step structureStep() {
        return new StepBuilder("structureStep", jobRepository)
                .tasklet(structureResolutionTasklet(null), tx)
                .build();
    }

    @Bean
    @StepScope
    public StructureResolutionTasklet structureResolutionTasklet(@Value("#{jobParameters['bookId']}") Long bookId) {
        return new StructureResolutionTasklet(
                bookId,
                bookRepository,
                pageRepository,
                sectionRepository,
                paginationModeDetector,
                tocAligner,
                headingsStructureBuilder,
                sectionTreeBuilder,
                meterRegistry
        );
    }

    // =========================
    // Step 5: Stitch & Harmonize
    // =========================

    @Bean
    public Step harmonizeStep() {
        return new StepBuilder("harmonizeStep", jobRepository)
                .tasklet(harmonizationTasklet(null), tx)
                .build();
    }

    @Bean
    @StepScope
    public HarmonizationTasklet harmonizationTasklet(@Value("#{jobParameters['bookId']}") Long bookId) {
        return new HarmonizationTasklet(
                bookId,
                pageRepository,
                harmonizationService,
                pageStitcher,
                properties,
                meterRegistry
        );
    }

    // =========================
    // Step 6: Quality Gate
    // =========================

    @Bean
    public Step qualityStep() {
        return new StepBuilder("qualityStep", jobRepository)
                .tasklet(qualityGateTasklet(null), tx)
                .build();
    }

    @Bean
    @StepScope
    public QualityGateTasklet qualityGateTasklet(@Value("#{jobParameters['bookId']}") Long bookId) {
        return new QualityGateTasklet(
                bookId,
                bookRepository,
                pageRepository,
                sectionRepository,
                properties,
                meterRegistry
        );
    }
}