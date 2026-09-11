package com.doova.ktab.controller.v1.internal;

import com.doova.ktab.config.qstash.QStashSignatureVerifier;
import com.doova.ktab.features.ocr.ai.GeminiOcrService;
import com.doova.ktab.features.ocr.dto.GeminiOcrResponse;
import com.doova.ktab.features.ocr.quota.DynamicConcurrencyGate;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrCallbackControllerTest {

    @Mock
    private QStashSignatureVerifier signatureVerifier;

    @Mock
    private GeminiOcrService geminiService;

    @Mock
    private DynamicConcurrencyGate concurrencyGate;

    @Mock
    private BookPageRepository sectionRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private EntityManager entityManager;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private OcrCallbackController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        controller = new OcrCallbackController(
                signatureVerifier,
                objectMapper,
                geminiService,
                concurrencyGate,
                sectionRepository,
                bookRepository,
                entityManager,
                meterRegistry
        );
    }

    @Test
    @DisplayName("processOcrPage returns 401 UNAUTHORIZED when signature verification fails")
    void processOcrPage_SignatureVerificationFails_Returns401() {
        when(signatureVerifier.verify(any(), anyString())).thenReturn(false);

        ResponseEntity<Void> response = controller.processOcrPage("invalid-sig", "{\"bookId\":1}");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(geminiService);
    }

    @Test
    @DisplayName("processOcrPage returns 400 BAD_REQUEST when JSON payload is invalid")
    void processOcrPage_InvalidJson_Returns400() {
        when(signatureVerifier.verify(any(), anyString())).thenReturn(true);

        ResponseEntity<Void> response = controller.processOcrPage("valid-sig", "invalid json");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(geminiService);
    }

    @Test
    @DisplayName("processOcrPage skips processing and returns 200 OK if page already exists")
    void processOcrPage_AlreadyProcessed_SkipsAndReturns200() {
        when(signatureVerifier.verify(any(), anyString())).thenReturn(true);
        when(sectionRepository.existsByBook_IdAndPageNumber(10L, 2)).thenReturn(true);

        String rawBody = """
                {"bookId":10,"pageNumber":2,"s3Key":"books/10/pages/page-0002.png","mime":"image/png","presignedUrl":"https://presigned"}
                """;

        ResponseEntity<Void> response = controller.processOcrPage("valid-sig", rawBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(sectionRepository).existsByBook_IdAndPageNumber(10L, 2);
        verifyNoInteractions(geminiService);
    }

    @Test
    @DisplayName("processOcrPage processes page with Gemini and saves BookPage on success")
    void processOcrPage_Success_ProcessesAndSavesPage() {
        when(signatureVerifier.verify(any(), anyString())).thenReturn(true);
        when(sectionRepository.existsByBook_IdAndPageNumber(10L, 1)).thenReturn(false);
        when(concurrencyGate.acquire()).thenReturn(new DynamicConcurrencyGate.Permit(new java.util.concurrent.Semaphore(1)));

        GeminiOcrResponse geminiResponse = new GeminiOcrResponse("# Chapter 1", 150);
        when(geminiService.ocrOnePageFromUrl("https://presigned", "image/png")).thenReturn(geminiResponse);

        Book mockBook = new Book();
        mockBook.setId(10L);
        when(entityManager.getReference(Book.class, 10L)).thenReturn(mockBook);

        String rawBody = """
                {"bookId":10,"pageNumber":1,"s3Key":"books/10/pages/page-0001.png","mime":"image/png","presignedUrl":"https://presigned"}
                """;

        ResponseEntity<Void> response = controller.processOcrPage("valid-sig", rawBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<BookPage> pageCaptor = ArgumentCaptor.forClass(BookPage.class);
        verify(sectionRepository).save(pageCaptor.capture());

        BookPage savedPage = pageCaptor.getValue();
        assertEquals(1, savedPage.getPageNumber());
        assertEquals("# Chapter 1", savedPage.getMarkdownContent());
        assertEquals(150, savedPage.getWordCount());
        assertEquals(mockBook, savedPage.getBook());
    }

    @Test
    @DisplayName("processOcrPage returns 500 when Gemini processing fails so QStash can retry")
    void processOcrPage_GeminiFails_Returns500ForRetry() {
        when(signatureVerifier.verify(any(), anyString())).thenReturn(true);
        when(sectionRepository.existsByBook_IdAndPageNumber(10L, 1)).thenReturn(false);
        when(concurrencyGate.acquire()).thenReturn(new DynamicConcurrencyGate.Permit(new java.util.concurrent.Semaphore(1)));
        when(geminiService.ocrOnePageFromUrl(anyString(), anyString())).thenThrow(new RuntimeException("Gemini quota exceeded"));

        String rawBody = """
                {"bookId":10,"pageNumber":1,"s3Key":"books/10/pages/page-0001.png","mime":"image/png","presignedUrl":"https://presigned"}
                """;

        ResponseEntity<Void> response = controller.processOcrPage("valid-sig", rawBody);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        verify(sectionRepository, never()).save(any());
    }
}
