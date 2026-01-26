package com.doova.ktab.utils.response;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.utils.wrapper.ContentWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.PrintWriter;

import static org.springframework.ai.model.ModelOptionsUtils.OBJECT_MAPPER;

public final class ResponseUtils {

    private ResponseUtils() {
    }

    // =========================================================================
    // LOW-LEVEL RESPONSE (FILTERS / SECURITY / SSE-SAFE)
    // =========================================================================

    /**
     * Write a JSON response directly to HttpServletResponse.
     * Used in filters, security handlers, and places where ResponseEntity
     * is not available.
     */
    public static void send(Object body, HttpServletResponse response, HttpStatus status) {
        try {
            if (response.isCommitted()) return;

            response.setStatus(status.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            PrintWriter writer = response.getWriter();
            writer.write(OBJECT_MAPPER.writeValueAsString(body));
            writer.flush();
        } catch (Exception ignored) {
            // Never throw from filters
        }
    }

    // =========================================================================
    // SUCCESS RESPONSES (ApiResponse)
    // =========================================================================

    public static <T> ResponseEntity<ApiResponse<T>> success(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.success(data, message, status));
    }

    public static <T> ResponseEntity<ApiResponse<T>> error(String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, status));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, message, HttpStatus.CREATED));
    }

    // =========================================================================
    // ERROR RESPONSES (THROWABLE)
    // =========================================================================

    /**
     * 400 Bad Request (message key expected).
     */
    public static ResponseStatusException badRequest(String messageKey) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, messageKey);
    }

    /**
     * 404 Not Found.
     */
    public static ResponseStatusException notFound(String messageKey) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, messageKey);
    }

    /**
     * Generic error with custom HTTP status.
     */
    public static ResponseStatusException errorResponse(String messageKey, HttpStatus status) {
        return new ResponseStatusException(status, messageKey);
    }

    // =========================================================================
    // PAGINATION / COLLECTION
    // =========================================================================

    /**
     * Return Spring Page<T> directly (contains metadata).
     */
    public static <T> ResponseEntity<Page<T>> page(Page<T> page) {
        return ResponseEntity.ok(page);
    }

    /**
     * Wrap Iterable<T> inside ContentWrapper.
     */
    public static <T> ResponseEntity<ContentWrapper<T>> collection(Iterable<T> iterable) {
        return ResponseEntity.ok(new ContentWrapper<>(iterable));
    }

    // =========================================================================
    // EMPTY RESPONSES
    // =========================================================================

    public static ResponseEntity<Void> ok() {
        return ResponseEntity.ok().build();
    }

    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }
}
