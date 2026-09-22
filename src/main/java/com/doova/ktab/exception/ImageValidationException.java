package com.doova.ktab.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Specialized exception for image validation failures.
 * Extends {@link IllegalArgumentException} for backward compatibility across existing service boundaries
 * while carrying structured diagnostic details (aspect ratio, dimensions, file size, format),
 * formatting arguments, and localized message keys.
 */
@Getter
public class ImageValidationException extends IllegalArgumentException {

    private final ApiMessageKey messageKey;
    private final Object[] args;
    private final Map<String, String> details;

    public ImageValidationException(ApiMessageKey messageKey, String message) {
        this(messageKey, null, message, Collections.emptyMap(), null);
    }

    public ImageValidationException(ApiMessageKey messageKey, String message, Map<String, String> details) {
        this(messageKey, null, message, details, null);
    }

    public ImageValidationException(ApiMessageKey messageKey, Object[] args, String message, Map<String, String> details) {
        this(messageKey, args, message, details, null);
    }

    public ImageValidationException(ApiMessageKey messageKey, Object[] args, String message, Map<String, String> details, Throwable cause) {
        super(message, cause);
        this.messageKey = messageKey;
        this.args = args != null ? args.clone() : new Object[0];
        this.details = details != null ? Collections.unmodifiableMap(new LinkedHashMap<>(details)) : Collections.emptyMap();
    }
}
