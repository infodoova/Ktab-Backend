package com.doova.ktab.exception.handler;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.sql.SQLIntegrityConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // =========================================================================
    // CUSTOM API EXCEPTIONS
    // =========================================================================

    @ExceptionHandler(KtabException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(KtabException ex) {
        HttpStatus status = ex.getHttpStatus() != null ? ex.getHttpStatus() : HttpStatus.BAD_REQUEST;
        log.debug("Business exception handled: {} -> status {}", ex.getMessageKey(), status);
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessageKey().getMessage(messageSource), status));
    }

    // =========================================================================
    // VALIDATION & REQUEST ERRORS
    // =========================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(org.springframework.context.support.DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse(ApiMessageKey.VALIDATION_FAILED.getMessage(messageSource));
        return ResponseEntity.badRequest().body(ApiResponse.error(message, HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse(ApiMessageKey.VALIDATION_FAILED.getMessage(messageSource));
        return ResponseEntity.badRequest().body(ApiResponse.error(message, HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequestErrors(Exception ex) {
        log.debug("Malformed client request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ApiMessageKey.VALIDATION_FAILED.getMessage(messageSource), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("Argument type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ApiMessageKey.VALIDATION_FAILED.getMessage(messageSource), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed: {} on current endpoint", ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("HTTP method " + ex.getMethod() + " is not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Content type is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("File upload size exceeded limit: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("Uploaded file exceeds the maximum permitted size", HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(jakarta.persistence.EntityNotFoundException ex) {
        String message = resolveMessageOrDefault(ex.getMessage(), ApiMessageKey.RESOURCE_NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(message, HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = resolveMessageOrDefault(ex.getMessage(), ApiMessageKey.VALIDATION_FAILED);
        return ResponseEntity.badRequest().body(ApiResponse.error(message, HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        String message = resolveMessageOrDefault(ex.getMessage(), ApiMessageKey.VALIDATION_FAILED);
        return ResponseEntity.badRequest().body(ApiResponse.error(message, HttpStatus.BAD_REQUEST));
    }

    // =========================================================================
    // AUTH & SECURITY
    // =========================================================================

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: invalid credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ApiMessageKey.AUTH_INVALID_CREDENTIALS.getMessage(messageSource), HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ApiMessageKey.ACCESS_DENIED.getMessage(messageSource), HttpStatus.FORBIDDEN));
    }

    // =========================================================================
    // DATABASE
    // =========================================================================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());
        Throwable root = getRootCause(ex);

        boolean isEmailViolation = false;
        if (root instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
            isEmailViolation = cve.getConstraintName().toLowerCase().contains("email");
        } else if (root instanceof SQLIntegrityConstraintViolationException sqlEx) {
            String sqlMsg = sqlEx.getMessage() != null ? sqlEx.getMessage().toLowerCase() : "";
            isEmailViolation = sqlMsg.contains("email") || sqlMsg.contains("col_email");
        }

        if (isEmailViolation) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(ApiMessageKey.AUTH_EMAIL_ALREADY_USED.getMessage(messageSource), HttpStatus.CONFLICT));
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ApiMessageKey.INTERNAL_ERROR.getMessage(messageSource), HttpStatus.CONFLICT));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException ex) {
        log.error("Database access failure: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ApiMessageKey.INTERNAL_ERROR.getMessage(messageSource), HttpStatus.INTERNAL_SERVER_ERROR));
    }

    // =========================================================================
    // FALLBACK
    // =========================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception encountered", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ApiMessageKey.INTERNAL_ERROR.getMessage(messageSource), HttpStatus.INTERNAL_SERVER_ERROR));
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String resolveMessageOrDefault(String keyOrMsg, ApiMessageKey defaultKey) {
        if (keyOrMsg != null && !keyOrMsg.isBlank()) {
            try {
                return messageSource.getMessage(keyOrMsg, null, org.springframework.context.i18n.LocaleContextHolder.getLocale());
            } catch (Exception ignored) {
                // If not found as a key, fallback to the default localized message
            }
        }
        return defaultKey.getMessage(messageSource);
    }

    private Throwable getRootCause(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
