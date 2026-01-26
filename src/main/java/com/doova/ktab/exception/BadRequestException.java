package com.doova.ktab.exception;

import com.doova.ktab.enums.ApiMessageKey;

public class BadRequestException extends KtabException {
    public BadRequestException(ApiMessageKey key) {
        super(key);
    }
}
