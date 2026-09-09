package com.doova.ktab.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import org.springframework.http.HttpStatus;

public class BadRequestException extends KtabException {

    public BadRequestException(ApiMessageKey key) {
        super(key);
    }

    public BadRequestException(ApiMessageKey key, Throwable cause) {
        super(key, cause);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
