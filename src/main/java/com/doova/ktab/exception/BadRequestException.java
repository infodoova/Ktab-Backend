package com.doova.ktab.exception;

import com.doova.ktab.enums.message.ApiMessageKey;

public class BadRequestException extends KtabException {
    public BadRequestException(ApiMessageKey key) {
        super(key);
    }
}
