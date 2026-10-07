package com.doova.ktab.features.earlyaccess.service;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.springframework.http.HttpStatus;

public class EarlyAccessConflictException extends KtabException {

    public EarlyAccessConflictException(ApiMessageKey key) {
        super(key);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
