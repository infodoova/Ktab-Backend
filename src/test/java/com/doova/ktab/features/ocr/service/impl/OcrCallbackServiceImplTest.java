package com.doova.ktab.features.ocr.service.impl;

import com.doova.ktab.config.qstash.QStashSignatureVerifier;
import com.doova.ktab.features.ocr.dto.OcrCallbackResult;
import com.doova.ktab.features.ocr.service.OcrPageProcessingService;
import com.doova.ktab.features.ocr.sqs.OcrPageMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrCallbackServiceImplTest {

    @Mock
    private QStashSignatureVerifier signatureVerifier;

    @Mock
    private OcrPageProcessingService pageProcessingService;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private OcrCallbackServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        service = new OcrCallbackServiceImpl(signatureVerifier, objectMapper, pageProcessingService, meterRegistry);
    }

    @Test
    @DisplayName("Returns INVALID_SIGNATURE and increments counter when signature verification fails")
    void processCallback_SignatureVerificationFails_ReturnsInvalidSignature() {
        when(signatureVerifier.verify(any(), any())).thenReturn(false);

        OcrCallbackResult result = service.processCallback("bad-sig", "{\"bookId\":1}");

        assertEquals(OcrCallbackResult.INVALID_SIGNATURE, result);
        assertEquals(1.0, meterRegistry.counter("ocr.qstash.unauthorized").count());
        verifyNoInteractions(pageProcessingService);
    }

    @Test
    @DisplayName("Returns INVALID_PAYLOAD and increments counter when JSON payload cannot be parsed")
    void processCallback_InvalidJson_ReturnsInvalidPayload() {
        when(signatureVerifier.verify(any(), any())).thenReturn(true);

        OcrCallbackResult result = service.processCallback("valid-sig", "not-a-valid-json");

        assertEquals(OcrCallbackResult.INVALID_PAYLOAD, result);
        assertEquals(1.0, meterRegistry.counter("ocr.qstash.parse.errors").count());
        verifyNoInteractions(pageProcessingService);
    }

    @Test
    @DisplayName("Returns SKIPPED when page is already processed (idempotent)")
    void processCallback_PageAlreadyProcessed_ReturnsSkipped() {
        when(signatureVerifier.verify(any(), any())).thenReturn(true);
        when(pageProcessingService.processPage(any(OcrPageMessage.class))).thenReturn(false);

        String rawBody = """
                {"bookId":10,"pageNumber":2,"s3Key":"books/10/pages/page-0002.png","mime":"image/png","presignedUrl":"https://presigned"}
                """;

        OcrCallbackResult result = service.processCallback("valid-sig", rawBody);

        assertEquals(OcrCallbackResult.SKIPPED, result);
        assertEquals(1.0, meterRegistry.counter("ocr.qstash.pages.skipped").count());
    }

    @Test
    @DisplayName("Returns SUCCESS and increments processed counter when page is successfully processed")
    void processCallback_PageSuccess_ReturnsSuccess() {
        when(signatureVerifier.verify(any(), any())).thenReturn(true);
        when(pageProcessingService.processPage(any(OcrPageMessage.class))).thenReturn(true);

        String rawBody = """
                {"bookId":10,"pageNumber":1,"s3Key":"books/10/pages/page-0001.png","mime":"image/png","presignedUrl":"https://presigned"}
                """;

        OcrCallbackResult result = service.processCallback("valid-sig", rawBody);

        assertEquals(OcrCallbackResult.SUCCESS, result);
        assertEquals(1.0, meterRegistry.counter("ocr.qstash.pages.processed").count());

        ArgumentCaptor<OcrPageMessage> captor = ArgumentCaptor.forClass(OcrPageMessage.class);
        verify(pageProcessingService).processPage(captor.capture());
        assertEquals(10L, captor.getValue().bookId());
        assertEquals(1, captor.getValue().pageNumber());
    }

    @Test
    @DisplayName("Returns PROCESSING_FAILED when page processing throws an exception")
    void processCallback_ProcessingThrowsException_ReturnsProcessingFailed() {
        when(signatureVerifier.verify(any(), any())).thenReturn(true);
        when(pageProcessingService.processPage(any(OcrPageMessage.class)))
                .thenThrow(new RuntimeException("Gemini quota exceeded"));

        String rawBody = """
                {"bookId":10,"pageNumber":1,"s3Key":"books/10/pages/page-0001.png","mime":"image/png","presignedUrl":"https://presigned"}
                """;

        OcrCallbackResult result = service.processCallback("valid-sig", rawBody);

        assertEquals(OcrCallbackResult.PROCESSING_FAILED, result);
        assertEquals(1.0, meterRegistry.counter("ocr.qstash.pages.failed").count());
    }
}
