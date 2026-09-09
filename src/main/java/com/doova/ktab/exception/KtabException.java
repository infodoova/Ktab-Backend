package com.doova.ktab.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class KtabException extends RuntimeException {

    private final ApiMessageKey messageKey;

    protected KtabException(ApiMessageKey messageKey) {
        super(messageKey != null ? messageKey.name() : null);
        this.messageKey = messageKey;
    }

    protected KtabException(ApiMessageKey messageKey, Throwable cause) {
        super(messageKey != null ? messageKey.name() : null, cause);
        this.messageKey = messageKey;
    }

    public abstract HttpStatus getHttpStatus();
}
