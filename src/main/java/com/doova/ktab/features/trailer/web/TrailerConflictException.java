package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.springframework.http.HttpStatus;

public class TrailerConflictException extends KtabException {

    public TrailerConflictException(ApiMessageKey key) {
        super(key);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
