package com.doova.ktab.exception;

import com.doova.ktab.enums.ApiMessageKey;
import lombok.Getter;

@Getter
public abstract class KtabException extends RuntimeException {

    private final ApiMessageKey messageKey;

    protected KtabException(ApiMessageKey messageKey) {
        this.messageKey = messageKey;
    }
}
