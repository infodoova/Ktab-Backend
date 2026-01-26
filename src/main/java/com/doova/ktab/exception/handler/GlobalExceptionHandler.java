package com.doova.ktab.exception.handler;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        HttpStatus status = resolveStatus(ex);

        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessageKey().getMessage(messageSource), status));
    }

    // =========================================================================
    // VALIDATION & REQUEST ERRORS
    // =========================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation() {
        return ResponseEntity.badRequest().body(ApiResponse.error(ApiMessageKey.VALIDATION_FAILED.getMessage(messageSource), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequestErrors() {
        return ResponseEntity.badRequest().body(ApiResponse.error(ApiMessageKey.VALIDATION_FAILED.getMessage(messageSource), HttpStatus.BAD_REQUEST));
    }

    // =========================================================================
    // AUTH & SECURITY
    // =========================================================================

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ApiMessageKey.AUTH_INVALID_CREDENTIALS.getMessage(messageSource), HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ApiMessageKey.ACCESS_DENIED.getMessage(messageSource), HttpStatus.FORBIDDEN));
    }

    // =========================================================================
    // DATABASE
    // =========================================================================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {

        Throwable root = getRootCause(ex);

        if (root instanceof ConstraintViolationException cve && cve.getConstraintName() != null && cve.getConstraintName().contains("email")) {

            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ApiMessageKey.AUTH_EMAIL_ALREADY_USED.getMessage(messageSource), HttpStatus.CONFLICT));
        }

        if (root instanceof SQLIntegrityConstraintViolationException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ApiMessageKey.AUTH_EMAIL_ALREADY_USED.getMessage(messageSource), HttpStatus.CONFLICT));
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ApiMessageKey.INTERNAL_ERROR.getMessage(messageSource), HttpStatus.CONFLICT));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(ApiMessageKey.INTERNAL_ERROR.getMessage(messageSource), HttpStatus.INTERNAL_SERVER_ERROR));
    }

    // =========================================================================
    // FALLBACK
    // =========================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(ApiMessageKey.INTERNAL_ERROR.getMessage(messageSource), HttpStatus.INTERNAL_SERVER_ERROR));
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private HttpStatus resolveStatus(KtabException ex) {
        if (ex instanceof BadRequestException) return HttpStatus.BAD_REQUEST;
        if (ex instanceof UnAuthorizedException) return HttpStatus.UNAUTHORIZED;
        if (ex instanceof ResourceNotFoundException) return HttpStatus.NOT_FOUND;
        if (ex instanceof S3UploadException) return HttpStatus.INTERNAL_SERVER_ERROR;
        return HttpStatus.BAD_REQUEST;
    }

    private Throwable getRootCause(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
