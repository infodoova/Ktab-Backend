package com.doova.ktab.exception;

import com.doova.ktab.enums.message.ApiMessageKey;

public class ResourceNotFoundException extends KtabException {
    public ResourceNotFoundException(ApiMessageKey key) {
        super(key);
    }
}
