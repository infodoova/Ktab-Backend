package com.doova.ktab.exception.handler;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private MessageSource messageSource;

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler(messageSource);
    }

    @Test
    @DisplayName("handleTimeout returns 504 Gateway Timeout for TimeoutException")
    void handleTimeout_whenTimeoutException_returnsGatewayTimeout() {
        when(messageSource.getMessage(eq(ApiMessageKey.REQUEST_TIMEOUT.getKey()), any(), any()))
                .thenReturn("Request processing timed out. Please try again.");

        TimeoutException ex = new TimeoutException("Operation timed out after 30s");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleTimeout(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatusCode()).isEqualTo(504);
        assertThat(response.getBody().getMessage()).contains("timed out");
    }

    @Test
    @DisplayName("handleTimeout returns 504 Gateway Timeout for SocketTimeoutException")
    void handleTimeout_whenSocketTimeoutException_returnsGatewayTimeout() {
        when(messageSource.getMessage(eq(ApiMessageKey.REQUEST_TIMEOUT.getKey()), any(), any()))
                .thenReturn("Request processing timed out. Please try again.");

        SocketTimeoutException ex = new SocketTimeoutException("Read timed out");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleTimeout(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatusCode()).isEqualTo(504);
    }

    @Test
    @DisplayName("handleTimeout returns 504 Gateway Timeout for AsyncRequestTimeoutException")
    void handleTimeout_whenAsyncRequestTimeoutException_returnsGatewayTimeout() {
        when(messageSource.getMessage(eq(ApiMessageKey.REQUEST_TIMEOUT.getKey()), any(), any()))
                .thenReturn("Request processing timed out. Please try again.");

        AsyncRequestTimeoutException ex = new AsyncRequestTimeoutException();
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleTimeout(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatusCode()).isEqualTo(504);
    }

    @Test
    @DisplayName("handleImageValidation returns 400 with detailed message and structured errors map")
    void handleImageValidation_withDetails_returnsBadRequestWithStructuredErrors() {
        java.util.Map<String, String> details = java.util.Map.of(
                "actualRatio", "1.20",
                "targetRatio", "1.60",
                "tolerance", "0.25",
                "width", "1000",
                "height", "1200"
        );
        String detailedMsg = "Invalid ratio: 1.20 (1000x1200)";
        com.doova.ktab.exception.ImageValidationException ex =
                new com.doova.ktab.exception.ImageValidationException(ApiMessageKey.IMAGE_INVALID_RATIO_COVER, detailedMsg, details);

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleImageValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(detailedMsg);
        assertThat(response.getBody().getErrors()).containsEntry("actualRatio", "1.20");
        assertThat(response.getBody().getErrors()).containsEntry("targetRatio", "1.60");
        assertThat(response.getBody().getErrors()).containsEntry("width", "1000");
    }

    @Test
    @DisplayName("handleIllegalArgument with custom message preserves custom message")
    void handleIllegalArgument_withCustomMessage_preservesCustomMessage() {
        when(messageSource.getMessage(eq("Custom validation error"), any(), any()))
                .thenThrow(new org.springframework.context.NoSuchMessageException("Custom validation error"));

        IllegalArgumentException ex = new IllegalArgumentException("Custom validation error");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Custom validation error");
    }

    @Test
    @DisplayName("handleImageValidation with unpopulated placeholder resolves and populates message from args")
    void handleImageValidation_withUnpopulatedPlaceholder_populatesFromArgs() {
        Object[] args = new Object[]{"1.25", "1080", "1350", "1.60", "1.35", "1.85", "0.25"};
        java.util.Map<String, String> details = java.util.Map.of("actualRatio", "1.25", "width", "1080");
        String unpopulated = "نسبة أبعاد صورة الغلاف غير مقبولة: النسبة الحالية {0} (الأبعاد: {1}×{2} بكسل). النسبة المطلوبة هي {3} تقريبًا (المدى المسموح: {4} إلى {5}، بنسبة سماحية ±{6}).";

        when(messageSource.getMessage(eq(ApiMessageKey.IMAGE_INVALID_RATIO_COVER.getKey()), any(), any()))
                .thenReturn("نسبة أبعاد صورة الغلاف غير مقبولة: النسبة الحالية 1.25 (الأبعاد: 1080×1350 بكسل). النسبة المطلوبة هي 1.60 تقريبًا (المدى المسموح: 1.35 إلى 1.85، بنسبة سماحية ±0.25).");

        com.doova.ktab.exception.ImageValidationException ex =
                new com.doova.ktab.exception.ImageValidationException(ApiMessageKey.IMAGE_INVALID_RATIO_COVER, args, unpopulated, details);

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleImageValidation(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).doesNotContain("{0}");
        assertThat(response.getBody().getMessage()).contains("1.25");
        assertThat(response.getBody().getMessage()).contains("1080×1350");
    }
}
