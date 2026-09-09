package com.doova.ktab.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends KtabException {

    public ResourceNotFoundException(ApiMessageKey key) {
        super(key);
    }

    public ResourceNotFoundException(ApiMessageKey key, Throwable cause) {
        super(key, cause);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
