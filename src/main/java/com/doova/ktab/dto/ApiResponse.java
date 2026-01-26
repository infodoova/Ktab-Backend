package com.doova.ktab.dto;

import com.doova.ktab.enums.status.MessageStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Standardized DTO for API responses.
 *
 * @param <T> The type of the data payload.
 */
@Data
@NoArgsConstructor
public class ApiResponse<T> {

    /**
     * Indicates logical success of the operation.
     * true  → SUCCESS
     * false → WARNING / ERROR
     */
    private boolean success;

    /**
     * HTTP status code (numeric) – frontend & logs friendly
     */
    private int statusCode;

    /**
     * HTTP status name (OK, CREATED, BAD_REQUEST, etc.)
     */
    private String status;

    /**
     * Semantic message status for UI handling
     */
    private MessageStatus messageStatus;

    /**
     * Localized message resolved from messages.properties
     */
    private String message;

    /**
     * ISO-8601 timestamp
     */
    private Instant timestamp;

    /**
     * Payload
     */
    private T data;

    // ========================================================================
    // FACTORY METHODS (PRODUCTION-READY)
    // ========================================================================

    public static <T> ApiResponse<T> success(T data, String message, HttpStatus httpStatus) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.messageStatus = MessageStatus.SUCCESS;
        response.data = data;
        response.message = message;
        response.statusCode = httpStatus.value();
        response.status = httpStatus.name();
        response.timestamp = Instant.now();
        return response;
    }

    public static <T> ApiResponse<T> warning(String message, HttpStatus httpStatus) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.messageStatus = MessageStatus.WARNING;
        response.data = null;
        response.message = message;
        response.statusCode = httpStatus.value();
        response.status = httpStatus.name();
        response.timestamp = Instant.now();
        return response;
    }

    public static <T> ApiResponse<T> error(String message, HttpStatus httpStatus) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.messageStatus = MessageStatus.ERROR;
        response.data = null;
        response.message = message;
        response.statusCode = httpStatus.value();
        response.status = httpStatus.name();
        response.timestamp = Instant.now();
        return response;
    }
}
