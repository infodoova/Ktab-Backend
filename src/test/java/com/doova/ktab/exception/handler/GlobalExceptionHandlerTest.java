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
}
