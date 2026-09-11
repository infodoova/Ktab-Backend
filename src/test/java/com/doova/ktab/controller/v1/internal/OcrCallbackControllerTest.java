package com.doova.ktab.controller.v1.internal;

import com.doova.ktab.features.ocr.dto.OcrCallbackResult;
import com.doova.ktab.features.ocr.service.OcrCallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrCallbackControllerTest {

    @Mock
    private OcrCallbackService ocrCallbackService;

    private OcrCallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new OcrCallbackController(ocrCallbackService);
    }

    @Test
    @DisplayName("Returns 401 UNAUTHORIZED when callback service returns INVALID_SIGNATURE")
    void processOcrPage_InvalidSignature_Returns401() {
        when(ocrCallbackService.processCallback(any(), any())).thenReturn(OcrCallbackResult.INVALID_SIGNATURE);

        ResponseEntity<Void> response = controller.processOcrPage("bad-sig", "{\"bookId\":1}");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(ocrCallbackService).processCallback("bad-sig", "{\"bookId\":1}");
    }

    @Test
    @DisplayName("Returns 400 BAD_REQUEST when callback service returns INVALID_PAYLOAD")
    void processOcrPage_InvalidPayload_Returns400() {
        when(ocrCallbackService.processCallback(any(), any())).thenReturn(OcrCallbackResult.INVALID_PAYLOAD);

        ResponseEntity<Void> response = controller.processOcrPage("sig", "bad json");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(ocrCallbackService).processCallback("sig", "bad json");
    }

    @Test
    @DisplayName("Returns 200 OK when callback service returns SKIPPED")
    void processOcrPage_Skipped_Returns200() {
        when(ocrCallbackService.processCallback(any(), any())).thenReturn(OcrCallbackResult.SKIPPED);

        ResponseEntity<Void> response = controller.processOcrPage("sig", "{\"bookId\":1}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(ocrCallbackService).processCallback("sig", "{\"bookId\":1}");
    }

    @Test
    @DisplayName("Returns 200 OK when callback service returns SUCCESS")
    void processOcrPage_Success_Returns200() {
        when(ocrCallbackService.processCallback(any(), any())).thenReturn(OcrCallbackResult.SUCCESS);

        ResponseEntity<Void> response = controller.processOcrPage("sig", "{\"bookId\":1}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(ocrCallbackService).processCallback("sig", "{\"bookId\":1}");
    }

    @Test
    @DisplayName("Returns 500 INTERNAL_SERVER_ERROR when callback service returns PROCESSING_FAILED")
    void processOcrPage_ProcessingFailed_Returns500() {
        when(ocrCallbackService.processCallback(any(), any())).thenReturn(OcrCallbackResult.PROCESSING_FAILED);

        ResponseEntity<Void> response = controller.processOcrPage("sig", "{\"bookId\":1}");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        verify(ocrCallbackService).processCallback("sig", "{\"bookId\":1}");
    }
}
