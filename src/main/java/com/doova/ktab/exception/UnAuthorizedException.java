package com.doova.ktab.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import org.springframework.http.HttpStatus;

public class UnAuthorizedException extends KtabException {

    public UnAuthorizedException(ApiMessageKey key) {
        super(key);
    }

    public UnAuthorizedException(ApiMessageKey key, Throwable cause) {
        super(key, cause);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
