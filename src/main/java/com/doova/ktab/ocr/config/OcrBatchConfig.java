package com.doova.ktab.ocr.config;

import com.doova.ktab.ocr.ai.GeminiOcrService;
import com.doova.ktab.ocr.batch.*;
import com.doova.ktab.ocr.pdf.PdfPageRenderer;
import com.doova.ktab.ocr.quota.DynamicConcurrencyGate;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.ocr.OcrFailureRepository;
import com.doova.ktab.service.ocr.S3OcrStorageService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
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
    private final BookPageRepository sections;
    private final OcrFailureRepository failures;
    private final BookRepository bookRepository;
    private final EntityManager em;
    private final PdfPageRenderer renderer;
    private final MeterRegistry meterRegistry;

    @Value("${ktab.ocr.chunkSize:20}")
    private int chunkSize;

    @Value("${ktab.ocr.retryLimit:3}")
    private int retryLimit;

    @Value("${ktab.ocr.systemPrompt}")
    private String systemPrompt;

    @Value("${ktab.ocr.dpi:300}")
    private int dpi;

    @Value("${ktab.ocr.threadPoolSize:20}")
    private int threadPoolSize;

    @Value("${ktab.ocr.queueCapacity:200}")
    private int queueCapacity;

    // Circuit breaker configuration
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
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize * 2);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ocr-thread-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        // Register metrics for thread pool monitoring
        meterRegistry.gauge("ocr.threadpool.active", executor, ThreadPoolTaskExecutor::getActiveCount);
        meterRegistry.gauge("ocr.threadpool.pool_size", executor, ThreadPoolTaskExecutor::getPoolSize);
        meterRegistry.gauge("ocr.threadpool.queue_size", executor, e -> e.getThreadPoolExecutor().getQueue().size());
        
        return executor;
    }

    // =========================
    // Job Definition
    // =========================

    @Bean
    public Job ocrJob(Step decompositionStep, Step ocrStep) {
        return new JobBuilder("ocrJob", jobRepository)
                .listener(new OcrCleanUpListener(bookRepository, s3))
                .start(decompositionStep)
                .next(ocrStep)
                .listener(new OcrCleanUpListener(bookRepository, s3))
                .build();
    }

    // =========================
    // Step 1: PDF Decomposition (Parallel Tasklet)
    // =========================

    @Bean
    public Step decompositionStep() {
        return new StepBuilder("decompositionStep", jobRepository)
                .tasklet(pdfToS3Tasklet(null, null), tx)
                .build();
    }

    @Bean
    @StepScope
    public PdfToS3Tasklet pdfToS3Tasklet(
            @Value("#{jobParameters['bookId']}") Long bookId,
            @Value("#{jobParameters['pdfKey']}") String pdfKey
    ) {
        return new PdfToS3Tasklet(renderer, s3, bookId, pdfKey, dpi, meterRegistry);
    }

    // =========================
    // Step 2: OCR Processing (Async with Presigned URLs)
    // =========================

    @Bean
    public Step ocrStep(
            AsyncItemProcessor<PageItem, OcrResult> asyncProcessor,
            AsyncItemWriter<OcrResult> asyncWriter,
            ItemReader<PageItem> pageReader
    ) {
        return new StepBuilder("ocrStep", jobRepository)
                .<PageItem, Future<OcrResult>>chunk(chunkSize, tx)
                .reader(pageReader)
                .processor(asyncProcessor)
                .writer(asyncWriter)
                .faultTolerant()
                .retryLimit(retryLimit)
                .retry(Exception.class)
                .skip(Exception.class)
                .skipLimit(Integer.MAX_VALUE)
                .listener(new OcrSkipListener(s3, failures, bookRepository, retryLimit))
                .build();
    }

    // =========================
    // Async Wrappers
    // =========================

    @Bean
    public AsyncItemProcessor<PageItem, OcrResult> asyncProcessor(GeminiOcrProcessor processor) {
        AsyncItemProcessor<PageItem, OcrResult> asyncProcessor = new AsyncItemProcessor<>();
        asyncProcessor.setDelegate(processor);
        asyncProcessor.setTaskExecutor(ocrTaskExecutor());
        return asyncProcessor;
    }

    @Bean
    public AsyncItemWriter<OcrResult> asyncWriter(BookSectionWriter writer) {
        AsyncItemWriter<OcrResult> asyncWriter = new AsyncItemWriter<>();
        asyncWriter.setDelegate(writer);
        return asyncWriter;
    }

    // =========================
    // Components
    // =========================

    @Bean
    public GeminiOcrProcessor ocrProcessor() {
        return new GeminiOcrProcessor(
                gemini,
                gate,
                systemPrompt,
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
    public ItemReader<PageItem> pageReader(@Value("#{jobParameters['bookId']}") Long bookId) {
        List<String> keys = s3.listPageKeys(bookId);
        return new RestartableS3PageReader(bookId, keys, s3, sections, meterRegistry);
    }
}