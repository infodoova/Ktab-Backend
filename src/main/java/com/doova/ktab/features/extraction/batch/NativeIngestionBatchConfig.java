package com.doova.ktab.features.extraction.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Placeholder until Task 5 replaces the step with the real extraction tasklet. */
@Configuration
public class NativeIngestionBatchConfig {

    @Bean
    public Job nativeIngestionJob(JobRepository repo, PlatformTransactionManager tx) {
        return new JobBuilder("nativeIngestionJob", repo)
                .start(new StepBuilder("nativeExtractStep", repo)
                        .tasklet((c, ctx) -> {
                            throw new IllegalStateException("nativeIngestionJob not implemented yet");
                        }, tx)
                        .build())
                .build();
    }
}
