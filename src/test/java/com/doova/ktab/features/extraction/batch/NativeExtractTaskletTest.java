package com.doova.ktab.features.extraction.batch;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.extraction.persist.ExtractionPersister;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.storage.ObjectStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.Optional;

import static com.doova.ktab.features.extraction.ExtractionTestSupport.fixture;
import static com.doova.ktab.features.extraction.ExtractionTestSupport.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NativeExtractTaskletTest {

    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ExtractionPersister persister = mock(ExtractionPersister.class);
    private final BookRepository books = mock(BookRepository.class);
    private final Book book = new Book();
    private NativeExtractTasklet tasklet;

    @BeforeEach
    void setUp() {
        when(books.findById(7L)).thenReturn(Optional.of(book));
        tasklet = new NativeExtractTasklet(7L, "k", storage, service(), persister, books, new SimpleMeterRegistry());
    }

    @Test
    void aDigitalBookIsExtractedAndSaved() throws Exception {
        when(storage.getBytes("k")).thenReturn(fixture("book-outline.pdf"));

        assertThat(tasklet.execute(null, null)).isEqualTo(RepeatStatus.FINISHED);

        verify(persister).persist(eq(book), any());
    }

    @Test
    void aScannedPdfFailsWithTheNoOcrMessageAndSavesNothing() {
        when(storage.getBytes("k")).thenReturn(fixture("image-only.pdf"));

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .hasMessageContaining("scanned books are not supported while OCR is off");

        verify(persister, never()).persist(any(), any());
        assertThat(book.getOcrStatus()).isEqualTo(OcrStatus.FAILED);
        verify(books).save(book);
    }

    @Test
    void anyRejectedPdfFailsTheBookWithTheReason() {
        when(storage.getBytes("k")).thenReturn(fixture("not-a-pdf.pdf"));

        assertThatThrownBy(() -> tasklet.execute(null, null)).hasMessageContaining("NOT_PDF");

        verify(persister, never()).persist(any(), any());
        assertThat(book.getOcrStatus()).isEqualTo(OcrStatus.FAILED);
    }
}
