package com.doova.ktab.features.extraction.batch;

import com.doova.ktab.features.extraction.BookExtractionService;
import com.doova.ktab.features.extraction.persist.ExtractionPersister;
import com.doova.ktab.features.nativetts.ChapterSynthesizer;
import com.doova.ktab.features.nativetts.batch.NativeAudiobookBatchConfig;
import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import com.doova.ktab.features.studio.repository.BookAudioChapterRepository;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.doova.ktab.service.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.StepScope;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Proves both new batch configs wire in a real Spring context: bean names, qualifiers and step-scoped tasklets. */
class NativeBatchConfigWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NativeIngestionBatchConfig.class, NativeAudiobookBatchConfig.class)
            .withBean(StepScope.class, StepScope::new)
            .withBean(JobRepository.class, () -> mock(JobRepository.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(ObjectStorageService.class, () -> mock(ObjectStorageService.class))
            .withBean(BookExtractionService.class, () -> mock(BookExtractionService.class))
            .withBean(ExtractionPersister.class, () -> mock(ExtractionPersister.class))
            .withBean(BookRepository.class, () -> mock(BookRepository.class))
            .withBean(BookSectionRepository.class, () -> mock(BookSectionRepository.class))
            .withBean(BookPageRepository.class, () -> mock(BookPageRepository.class))
            .withBean(BookAudioChapterRepository.class, () -> mock(BookAudioChapterRepository.class))
            .withBean(ChapterSynthesizer.class, () -> mock(ChapterSynthesizer.class))
            .withBean(S3Client.class, () -> mock(S3Client.class))
            .withBean(NativeTtsProperties.class, NativeTtsProperties::new)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void bothNativeJobsAreBeansUnderTheNamesTheRoutersInjectByQualifier() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBeansOfType(Job.class)).containsKeys("nativeIngestionJob", "nativeAudiobookJob");
            assertThat(ctx.getBean("nativeIngestionJob", Job.class).getName()).isEqualTo("nativeIngestionJob");
            assertThat(ctx.getBean("nativeAudiobookJob", Job.class).getName()).isEqualTo("nativeAudiobookJob");
        });
    }
}
