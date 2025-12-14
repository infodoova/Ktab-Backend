package com.doova.ktab.api.exceptionHandler;

import com.doova.ktab.exceptions.ResourceNotFoundException;
import com.doova.ktab.exceptions.S3UploadException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLIntegrityConstraintViolationException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Helpers -------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, Object message, HttpServletRequest request) {
        return buildResponse(status, error, message, null, request);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, Object message, Map<String, ?> extra, HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getRequestURI());

        if (extra != null && !extra.isEmpty()) {
            body.putAll(extra);
        }

        return ResponseEntity.status(status).body(body);
    }

    // ---- 400 / Bad Request Exceptions ---------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(FieldError::getField, fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value", (first, ignored) -> first));

        logger.error("Validation error", ex);

        Map<String, Object> extra = new HashMap<>();
        extra.put("fields", fields);

        return buildResponse(HttpStatus.BAD_REQUEST, "validation_error", "One or more fields are invalid", extra, request);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class})
    public ResponseEntity<Map<String, Object>> handleMissingParamsAndVariables(Exception ex, HttpServletRequest request) {
        logger.error("Missing required parameter/variable", ex);
        return buildResponse(HttpStatus.BAD_REQUEST, "missing_parameter_or_variable", ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        logger.error("Request body not readable/malformed JSON", ex);
        String message = ex.getCause() != null ? ex.getCause().getMessage() : "Malformed JSON body or type mismatch in request payload.";
        return buildResponse(HttpStatus.BAD_REQUEST, "invalid_request_body", message, request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        logger.error("Bad request", ex);
        return buildResponse(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        logger.error("Illegal argument", ex);

        HttpStatus status = HttpStatus.BAD_REQUEST;
        String code = "invalid_argument";

        if ("Email is already in use".equals(ex.getMessage())) {
            status = HttpStatus.CONFLICT;
            code = "email_in_use";
        }

        return buildResponse(status, code, ex.getMessage(), request);
    }

    // ---- 404 / Not Found Exceptions -----------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        logger.error("Resource not found", ex);
        return buildResponse(HttpStatus.NOT_FOUND, "not_found", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        logger.error("No resource found", ex);
        return buildResponse(HttpStatus.NOT_FOUND, "resource_not_found", ex.getMessage(), request);
    }

    // ---- 405 / Method Not Allowed -------------------------------------------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        logger.error("Method Not Allowed", ex);

        String supportedMethods = ex.getSupportedHttpMethods() != null ? "Supported methods: " + ex.getSupportedHttpMethods().stream().map(Object::toString).collect(Collectors.joining(", ")) : "Method not supported for this resource.";

        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", supportedMethods, request);
    }

    // ---- Auth / Security Exceptions -----------------------------------------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        logger.error("Bad credentials", ex);

        return buildResponse(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Email or password is incorrect", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        logger.error("Access denied", ex);
        return buildResponse(HttpStatus.FORBIDDEN, "access_denied", ex.getMessage(), request);
    }

    // ---- Database/Persistence Errors (409 Conflict) -------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        logger.error("Data Integrity Violation: A database constraint was violated.", ex);

        Throwable root = getRootCause(ex);

        // --- Case 1: Hibernate ConstraintViolationException (commonly wraps unique constraints)
        if (root instanceof ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            String errorCode = "unique_constraint_violation";
            String message = "A record with the same value already exists.";

            // OPTIONAL: customize per-constraint name if you want
            if (constraintName != null) {
                if (constraintName.contains("uq_users_email")) {
                    errorCode = "email_in_use";
                    message = "Email is already in use.";
                }
                // you can add more constraint name checks here
            }

            return buildResponse(HttpStatus.CONFLICT, errorCode, message, request);
        }

        // --- Case 2: Pure JDBC unique constraint (e.g. SQLIntegrityConstraintViolationException)
        if (root instanceof SQLIntegrityConstraintViolationException) {
            return buildResponse(HttpStatus.CONFLICT, "unique_constraint_violation", "A record with the same value already exists.", request);
        }

        // --- Fallback: other data integrity issues
        return buildResponse(HttpStatus.CONFLICT, "data_conflict", "The requested operation violates a data constraint (e.g., duplicate unique entry, missing required data).", request);
    }


    // ---- External Service Errors / 500 --------------------------------------

    @ExceptionHandler(S3UploadException.class)
    public ResponseEntity<Map<String, Object>> handleS3Error(S3UploadException ex, HttpServletRequest request) {
        logger.error("S3 Upload Error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "file_upload_failed", ex.getMessage(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(DataAccessException ex, HttpServletRequest request) {
        logger.error("Data Access Error: Persistence operation failed.", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "persistence_error", "A system error occurred while accessing the database.", request);
    }

    // ---- Generic Fallback ---------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex, HttpServletRequest request) {
        logger.error("Unexpected error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error", ex.getMessage(), request);
    }

    private Throwable getRootCause(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
