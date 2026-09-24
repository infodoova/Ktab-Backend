package com.doova.ktab.features.studio.config;

import com.doova.ktab.features.ingestion.routing.BookContentPurger;
import com.doova.ktab.features.studio.batch.*;
import com.doova.ktab.features.studio.client.ElevenLabsStudioClient;
import com.doova.ktab.features.studio.repository.BookAudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.doova.ktab.features.studio.sync.StudioSyncService;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Spring Batch configuration for the independent Studio ingestion and audiobook pipeline
 * ({@code features.studio}).
 *
 * <p>Enforces architectural independence: imports nothing from {@code features.ocr}.
 * See docs/ocr_engine_v3.md, "Two independent pipelines, one output contract".
 */
@Configuration
@RequiredArgsConstructor
public class StudioBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager tx;
    private final TransactionTemplate transactionTemplate;
    private final StudioProperties properties;

    private final ElevenLabsStudioClient studioClient;
    private final StudioProjectRepository studioProjectRepository;
    private final StudioChapterRepository studioChapterRepository;
    private final BookAudioChapterRepository bookAudioChapterRepository;
    private final BookRepository bookRepository;
    private final BookPageRepository bookPageRepository;
    private final BookSectionRepository bookSectionRepository;
    private final BookContentPurger purger;
    private final StudioSyncService studioSyncService;
    private final ObjectStorageService storage;
    private final AttachmentService attachmentService;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}")
    private String bucketName;

    // =========================================================================
    // Infrastructure
    // =========================================================================

    @Bean
    public TaskExecutor studioTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAudiobook().getMaxConcurrentConversions());
        executor.setMaxPoolSize(properties.getAudiobook().getMaxConcurrentConversions() * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("studio-batch-");
        executor.initialize();
        return executor;
    }

    // =========================================================================
    // Jobs
    // =========================================================================

    @Bean(name = "studioIngestionJob")
    public Job studioIngestionJob(
            Step studioCreateProjectStep,
            Step studioSyncContentStep,
            Step studioRenumberStep,
            Step studioProjectionGateStep
    ) {
        return new JobBuilder("studioIngestionJob", jobRepository)
                .start(studioCreateProjectStep)
                .next(studioSyncContentStep)
                .next(studioRenumberStep)
                .next(studioProjectionGateStep)
                .build();
    }

    @Bean(name = "studioAudiobookJob")
    public Job studioAudiobookJob(
            Step studioEstimateStep,
            Step studioCreateProjectStep,
            Step studioPushChaptersStep,
            Step studioConvertStep,
            Step studioPollStep,
            Step studioDownloadStep,
            Step studioTimingIndexStep,
            Step studioFinalizeStep,
            Step studioCleanupStep
    ) {
        return new JobBuilder("studioAudiobookJob", jobRepository)
                .start(studioEstimateStep)
                .next(studioCreateProjectStep)
                .next(studioPushChaptersStep)
                .next(studioConvertStep)
                .next(studioPollStep)
                .next(studioDownloadStep)
                .next(studioTimingIndexStep)
                .next(studioFinalizeStep)
                .next(studioCleanupStep)
                .build();
    }

    // =========================================================================
    // Steps
    // =========================================================================

    @Bean
    public Step studioEstimateStep() {
        return new StepBuilder("studioEstimateStep", jobRepository)
                .tasklet(studioEstimateTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioCreateProjectStep() {
        return new StepBuilder("studioCreateProjectStep", jobRepository)
                .tasklet(studioCreateProjectTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioSyncContentStep() {
        return new StepBuilder("studioSyncContentStep", jobRepository)
                .tasklet(studioSyncContentTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioRenumberStep() {
        return new StepBuilder("studioRenumberStep", jobRepository)
                .tasklet(studioRenumberTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioProjectionGateStep() {
        return new StepBuilder("studioProjectionGateStep", jobRepository)
                .tasklet(studioProjectionGateTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioPushChaptersStep() {
        return new StepBuilder("studioPushChaptersStep", jobRepository)
                .tasklet(studioPushChaptersTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioConvertStep() {
        return new StepBuilder("studioConvertStep", jobRepository)
                .tasklet(studioConvertTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioPollStep() {
        return new StepBuilder("studioPollStep", jobRepository)
                .tasklet(studioPollTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioDownloadStep() {
        return new StepBuilder("studioDownloadStep", jobRepository)
                .tasklet(studioDownloadTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioTimingIndexStep() {
        return new StepBuilder("studioTimingIndexStep", jobRepository)
                .tasklet(studioTimingIndexTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioFinalizeStep() {
        return new StepBuilder("studioFinalizeStep", jobRepository)
                .tasklet(studioFinalizeTasklet(null), tx)
                .build();
    }

    @Bean
    public Step studioCleanupStep() {
        return new StepBuilder("studioCleanupStep", jobRepository)
                .tasklet(studioCleanupTasklet(null), tx)
                .build();
    }

    // =========================================================================
    // Tasklets (StepScoped)
    // =========================================================================

    @Bean
    @StepScope
    public EstimateTasklet studioEstimateTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new EstimateTasklet(bookId, bookPageRepository, properties);
    }

    @Bean
    @StepScope
    public CreateProjectTasklet studioCreateProjectTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new CreateProjectTasklet(
                bookId,
                studioClient,
                studioProjectRepository,
                bookRepository,
                storage,
                attachmentService,
                properties
        );
    }

    @Bean
    @StepScope
    public SyncContentTasklet studioSyncContentTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new SyncContentTasklet(bookId, studioSyncService, studioProjectRepository, properties);
    }

    @Bean
    @StepScope
    public StudioRenumberTasklet studioRenumberTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new StudioRenumberTasklet(
                bookId,
                studioProjectRepository,
                studioChapterRepository,
                bookPageRepository,
                bookSectionRepository,
                bookRepository
        );
    }

    @Bean
    @StepScope
    public ProjectionGateTasklet studioProjectionGateTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new ProjectionGateTasklet(
                bookId,
                studioProjectRepository,
                studioChapterRepository,
                bookPageRepository,
                bookRepository,
                purger,
                properties,
                meterRegistry
        );
    }

    @Bean
    @StepScope
    public PushChaptersTasklet studioPushChaptersTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new PushChaptersTasklet(
                bookId,
                studioClient,
                studioProjectRepository,
                studioChapterRepository,
                bookSectionRepository,
                bookPageRepository,
                properties,
                transactionTemplate
        );
    }

    @Bean
    @StepScope
    public ConvertTasklet studioConvertTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new ConvertTasklet(bookId, studioClient, studioProjectRepository, properties);
    }

    @Bean
    @StepScope
    public PollTasklet studioPollTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new PollTasklet(bookId, studioSyncService, studioProjectRepository, properties);
    }

    @Bean
    @StepScope
    public DownloadTasklet studioDownloadTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new DownloadTasklet(
                bookId,
                studioClient,
                studioProjectRepository,
                studioChapterRepository,
                s3Client,
                bucketName,
                properties,
                meterRegistry
        );
    }

    @Bean
    @StepScope
    public TimingIndexTasklet studioTimingIndexTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new TimingIndexTasklet(
                bookId,
                studioClient,
                studioProjectRepository,
                studioChapterRepository,
                s3Client,
                bucketName,
                objectMapper,
                properties
        );
    }

    @Bean
    @StepScope
    public FinalizeTasklet studioFinalizeTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new FinalizeTasklet(
                bookId,
                studioProjectRepository,
                studioChapterRepository,
                bookAudioChapterRepository
        );
    }

    @Bean
    @StepScope
    public CleanupTasklet studioCleanupTasklet(
            @Value("#{jobParameters['bookId']}") Long bookId
    ) {
        return new CleanupTasklet(
                bookId,
                studioClient,
                studioProjectRepository,
                properties
        );
    }
}
