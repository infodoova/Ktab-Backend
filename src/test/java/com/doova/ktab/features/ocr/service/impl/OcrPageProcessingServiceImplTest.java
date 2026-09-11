package com.doova.ktab.features.ocr.service.impl;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.ocr.ai.GeminiOcrService;
import com.doova.ktab.features.ocr.dto.GeminiOcrResponse;
import com.doova.ktab.features.ocr.quota.DynamicConcurrencyGate;
import com.doova.ktab.features.ocr.sqs.OcrPageMessage;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrPageProcessingServiceImplTest {

    @Mock
    private GeminiOcrService geminiService;

    @Mock
    private DynamicConcurrencyGate concurrencyGate;

    @Mock
    private BookPageRepository sectionRepository;

    @Mock
    private EntityManager entityManager;

    private OcrPageProcessingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OcrPageProcessingServiceImpl(
                geminiService,
                concurrencyGate,
                sectionRepository,
                entityManager
        );
    }

    @Test
    @DisplayName("Returns false and skips processing when page already exists in repository")
    void processPage_AlreadyExists_ReturnsFalse() {
        when(sectionRepository.existsByBook_IdAndPageNumber(10L, 2)).thenReturn(true);

        OcrPageMessage message = OcrPageMessage.create(10L, 2, "books/10/pages/page-0002.png", "image/png", "https://presigned");
        boolean result = service.processPage(message);

        assertFalse(result);
        verify(sectionRepository).existsByBook_IdAndPageNumber(10L, 2);
        verifyNoInteractions(concurrencyGate);
        verifyNoInteractions(geminiService);
        verifyNoInteractions(entityManager);
        verify(sectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Acquires concurrency permit, calls Gemini, constructs and saves BookPage on success")
    void processPage_Success_SavesBookPageAndReturnsTrue() {
        when(sectionRepository.existsByBook_IdAndPageNumber(10L, 1)).thenReturn(false);
        when(concurrencyGate.acquire()).thenReturn(new DynamicConcurrencyGate.Permit(new Semaphore(1)));

        GeminiOcrResponse geminiResponse = new GeminiOcrResponse("# Chapter 1\nText content", 120);
        when(geminiService.ocrOnePageFromUrl("https://presigned", "image/png")).thenReturn(geminiResponse);

        Book mockBook = new Book();
        mockBook.setId(10L);
        when(entityManager.getReference(Book.class, 10L)).thenReturn(mockBook);

        OcrPageMessage message = OcrPageMessage.create(10L, 1, "books/10/pages/page-0001.png", "image/png", "https://presigned");
        boolean result = service.processPage(message);

        assertTrue(result);

        ArgumentCaptor<BookPage> pageCaptor = ArgumentCaptor.forClass(BookPage.class);
        verify(sectionRepository).save(pageCaptor.capture());

        BookPage saved = pageCaptor.getValue();
        assertEquals(1, saved.getPageNumber());
        assertEquals("# Chapter 1\nText content", saved.getMarkdownContent());
        assertEquals(120, saved.getWordCount());
        assertEquals(OcrStatus.COMPLETED, saved.getStatus());
        assertEquals(mockBook, saved.getBook());
    }

    @Test
    @DisplayName("Propagates exception and does not save page when Gemini service fails")
    void processPage_GeminiFails_ThrowsException() {
        when(sectionRepository.existsByBook_IdAndPageNumber(10L, 1)).thenReturn(false);
        when(concurrencyGate.acquire()).thenReturn(new DynamicConcurrencyGate.Permit(new Semaphore(1)));
        when(geminiService.ocrOnePageFromUrl(anyString(), anyString())).thenThrow(new RuntimeException("Gemini quota exceeded"));

        OcrPageMessage message = OcrPageMessage.create(10L, 1, "books/10/pages/page-0001.png", "image/png", "https://presigned");

        assertThrows(RuntimeException.class, () -> service.processPage(message));
        verify(sectionRepository, never()).save(any());
    }
}
