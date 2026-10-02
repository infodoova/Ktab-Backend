package com.doova.ktab.features.nativetts.batch;

import com.doova.ktab.features.nativetts.ChapterSynthesizer;
import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import com.doova.ktab.features.studio.repository.BookAudioChapterRepository;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;

/** nativeAudiobookJob: estimate, then read every chapter aloud. Writes what Studio's audiobook job writes. */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class NativeAudiobookBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager tx;
    private final NativeTtsProperties props;
    private final ChapterSynthesizer synthesizer;
    private final BookRepository bookRepository;
    private final BookSectionRepository sectionRepository;
    private final BookPageRepository pageRepository;
    private final BookAudioChapterRepository audioRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;

    @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}")
    private String bucketName;

    @Bean(name = "nativeAudiobookJob")
    public Job nativeAudiobookJob(Step nativeEstimateStep, Step nativeSynthesizeStep) {
        return new JobBuilder("nativeAudiobookJob", jobRepository).start(nativeEstimateStep).next(nativeSynthesizeStep).build();
    }

    @Bean
    public Step nativeEstimateStep(NativeEstimateTasklet nativeEstimateTasklet) {
        return new StepBuilder("nativeEstimateStep", jobRepository).tasklet(nativeEstimateTasklet, tx).build();
    }

    @Bean
    public Step nativeSynthesizeStep(NativeSynthesizeTasklet nativeSynthesizeTasklet) {
        return new StepBuilder("nativeSynthesizeStep", jobRepository).tasklet(nativeSynthesizeTasklet, tx).build();
    }

    @Bean
    @StepScope
    public NativeEstimateTasklet nativeEstimateTasklet(@Value("#{jobParameters['bookId']}") Long bookId) {
        return new NativeEstimateTasklet(bookId, props, pageRepository);
    }

    @Bean
    @StepScope
    public NativeSynthesizeTasklet nativeSynthesizeTasklet(@Value("#{jobParameters['bookId']}") Long bookId) {
        TransactionTemplate own = new TransactionTemplate(tx);
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new NativeSynthesizeTasklet(bookId, props, synthesizer, bookRepository, sectionRepository, pageRepository,
                audioRepository, s3Client, bucketName, objectMapper, meters, r -> own.executeWithoutResult(s -> r.run()));
    }
}
