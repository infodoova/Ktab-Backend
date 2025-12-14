package com.doova.ktab.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Standardized DTO for API responses, wrapping the payload data with metadata.
 * Uses generics for type safety on the 'data' field.
 *
 * @param <T> The type of the data payload.
 */
@Data
@NoArgsConstructor // Lombok annotation to generate a constructor with no arguments
public class ApiResponse<T> {

    private boolean success;
    private String status; // HTTP status code or description (e.g., "OK", "CREATED")
    private String message;
    private String timestamp;
    private T data; // Generic field for the response payload

    // --- Constructor for Success Responses (Used by ResponseUtils) ---

    public ApiResponse(T data, String message) {
        this.success = true;
        this.message = message;
        this.data = data;
        this.status = HttpStatus.OK.name(); // Default to OK for success
        this.timestamp = java.time.LocalDateTime.now().toString();
    }

    // --- Static Factory Method for Success Responses (Best Practice) ---

    /**
     * Factory method for creating a success response.
     *
     * @param data    The data payload.
     * @param message The message to the user.
     * @param <T>     The type of the data payload.
     * @return A new ApiResponse instance.
     */
    public static <T> ApiResponse<T> success(T data, String message, HttpStatus httpStatus) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        response.message = message;
        response.status = httpStatus.name();
        response.timestamp = java.time.LocalDateTime.now().toString();
        return response;
    }

    // --- Static Factory Method for Error Responses (Optional but helpful) ---

    /**
     * Factory method for creating an error response.
     *
     * @param message    The error message.
     * @param httpStatus The HttpStatus code indicating the error.
     * @return A new ApiResponse instance marked as failure.
     */
    public static <T> ApiResponse<T> error(String message, HttpStatus httpStatus) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.data = null; // No data on error
        response.message = message;
        response.status = httpStatus.name();
        response.timestamp = java.time.LocalDateTime.now().toString();
        return response;
    }

}