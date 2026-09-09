package com.doova.ktab.utils.response;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.utils.wrapper.ContentWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

@Slf4j
public final class ResponseUtils {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private ResponseUtils() {
    }

    /**
     * Writes a JSON response directly to HttpServletResponse.
     * Intended for filters/security handlers where ResponseEntity is unavailable.
     */
    public static void send(Object body, HttpServletResponse response, HttpStatus status) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(status, "status must not be null");

        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        try {
            PrintWriter writer = response.getWriter();
            writer.write(OBJECT_MAPPER.writeValueAsString(body));
            writer.flush();
        } catch (IOException e) {
            log.error("Failed to write HTTP JSON response", e);
        }
    }

    public static <T> ResponseEntity<ApiResponse<T>> success(T data, String message, HttpStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return ResponseEntity.status(status)
                .body(ApiResponse.success(data, message, status));
    }

    public static <T> ResponseEntity<ApiResponse<T>> error(String message, HttpStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return ResponseEntity.status(status)
                .body(ApiResponse.error(message, status));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        return success(data, message, HttpStatus.CREATED);
    }

    public static ResponseStatusException badRequest(String messageKey) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, messageKey);
    }

    public static ResponseStatusException notFound(String messageKey) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, messageKey);
    }

    public static ResponseStatusException errorResponse(String messageKey, HttpStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return new ResponseStatusException(status, messageKey);
    }

    public static <T> ResponseEntity<Page<T>> page(Page<T> page) {
        return ResponseEntity.ok(Objects.requireNonNull(page, "page must not be null"));
    }

    public static <T> ResponseEntity<ContentWrapper<T>> collection(Iterable<T> iterable) {
        return ResponseEntity.ok(new ContentWrapper<>(
                Objects.requireNonNull(iterable, "iterable must not be null")
        ));
    }

    public static ResponseEntity<Void> ok() {
        return ResponseEntity.ok().build();
    }

    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }
}
