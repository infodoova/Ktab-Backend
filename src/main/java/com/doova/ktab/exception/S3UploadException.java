package com.doova.ktab.exception;

import com.doova.ktab.enums.ApiMessageKey;

public class S3UploadException extends KtabException {

    public S3UploadException(ApiMessageKey key) {
        super(key);
    }

    public S3UploadException(ApiMessageKey key, Throwable cause) {
        super(key);
        initCause(cause);
    }
}
