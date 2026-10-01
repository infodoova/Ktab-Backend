package com.doova.ktab.features.extraction.batch;

import com.doova.ktab.features.extraction.BookExtractionService;
import com.doova.ktab.features.extraction.persist.ExtractionPersister;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.storage.ObjectStorageService;
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
import org.springframework.transaction.PlatformTransactionManager;

/** nativeIngestionJob: Ktab's own extraction of a PDF's text layer into the shared book tables. */
@Configuration
@RequiredArgsConstructor
public class NativeIngestionBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager tx;
    private final ObjectStorageService storage;
    private final BookExtractionService extraction;
    private final ExtractionPersister persister;
    private final BookRepository bookRepository;
    private final MeterRegistry meters;

    @Bean(name = "nativeIngestionJob")
    public Job nativeIngestionJob(Step nativeExtractStep) {
        return new JobBuilder("nativeIngestionJob", jobRepository).start(nativeExtractStep).build();
    }

    @Bean
    public Step nativeExtractStep() {
        return new StepBuilder("nativeExtractStep", jobRepository).tasklet(nativeExtractTasklet(null, null), tx).build();
    }

    @Bean
    @StepScope
    public NativeExtractTasklet nativeExtractTasklet(@Value("#{jobParameters['bookId']}") Long bookId,
                                                     @Value("#{jobParameters['pdfKey']}") String pdfKey) {
        return new NativeExtractTasklet(bookId, pdfKey, storage, extraction, persister, bookRepository, meters);
    }
}
