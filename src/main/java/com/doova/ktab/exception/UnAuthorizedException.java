package com.doova.ktab.exception;

import com.doova.ktab.enums.message.ApiMessageKey;

public class UnAuthorizedException extends KtabException {
    public UnAuthorizedException(ApiMessageKey key) {
        super(key);
    }
}
