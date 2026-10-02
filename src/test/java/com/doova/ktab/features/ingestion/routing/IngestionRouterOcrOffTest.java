package com.doova.ktab.features.ingestion.routing;

import com.doova.ktab.enums.book.IngestionRoute;
import com.doova.ktab.enums.book.PdfType;
import com.doova.ktab.features.ingestion.config.IngestionProperties;
import com.doova.ktab.features.ingestion.config.OcrSwitchProperties;
import com.doova.ktab.features.ingestion.pdf.PdfClassificationResult;
import com.doova.ktab.features.ingestion.pdf.PdfTypeClassifier;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IngestionRouterOcrOffTest {

    private final PdfTypeClassifier classifier = mock(PdfTypeClassifier.class);
    private final BookContentPurger purger = mock(BookContentPurger.class);
    private final BookRepository books = mock(BookRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final JobExplorer explorer = mock(JobExplorer.class);
    private final Job ocrJob = mock(Job.class);
    private final Job studioJob = mock(Job.class);
    private final Job nativeJob = mock(Job.class);
    private final StudioProperties studio = new StudioProperties();
    private final OcrSwitchProperties ocr = new OcrSwitchProperties();
    private IngestionRouter router;

    @BeforeEach
    void setUp() throws Exception {
        router = new IngestionRouter(classifier, purger, books, storage, new ObjectMapper(), new IngestionProperties(),
                new SimpleMeterRegistry(), jobLauncher, explorer, ocrJob, studioJob, nativeJob, studio, ocr);
        Book book = new Book();
        book.setLanguage("ar");
        when(books.findById(7L)).thenReturn(Optional.of(book));
        when(storage.getStream("k")).thenAnswer(i -> new ByteArrayInputStream(new byte[0]));
        when(explorer.findRunningJobExecutions(any())).thenReturn(Set.of());
        when(jobLauncher.run(any(Job.class), any())).thenReturn(mock(JobExecution.class));
    }

    private void classifiedAs(PdfType type) {
        when(classifier.classify(any(), any()))
                .thenReturn(PdfClassificationResult.builder().pdfType(type).reason("t").build());
    }

    @Test
    void aScannedBookRunsNativeAndOcrJobIsNeverLaunchedWhileOcrIsOff() throws Exception {
        classifiedAs(PdfType.SCANNED);

        IngestionRoute route = router.ingest(7L, "k");

        assertThat(route).isEqualTo(IngestionRoute.NATIVE);
        verify(jobLauncher).run(eq(nativeJob), any());
        verify(jobLauncher, never()).run(eq(ocrJob), any());
    }

    @Test
    void aDigitalBookRunsNativeWhileStudioIsOffAndStudioWhenOn() throws Exception {
        classifiedAs(PdfType.DIGITAL);

        router.ingest(7L, "k");
        verify(jobLauncher).run(eq(nativeJob), any());

        studio.setEnabled(true);
        router.ingest(7L, "k");
        verify(jobLauncher).run(eq(studioJob), any());
        verify(jobLauncher, never()).run(eq(ocrJob), any());
    }

    @Test
    void withOcrOnAScannedBookGoesToOcrAsBefore() throws Exception {
        ocr.setEnabled(true);
        classifiedAs(PdfType.SCANNED);

        assertThat(router.ingest(7L, "k")).isEqualTo(IngestionRoute.OCR);
        verify(jobLauncher).run(eq(ocrJob), any());
    }

    @Test
    void aRunningNativeJobBlocksANewIngestion() throws Exception {
        classifiedAs(PdfType.DIGITAL);
        JobExecution running = mock(JobExecution.class);
        when(running.getJobParameters()).thenReturn(new JobParametersBuilder().addLong("bookId", 7L).toJobParameters());
        when(explorer.findRunningJobExecutions("nativeIngestionJob")).thenReturn(Set.of(running));

        assertThat(router.ingest(7L, "k")).isNull();
        verifyNoInteractions(jobLauncher);
    }
}
