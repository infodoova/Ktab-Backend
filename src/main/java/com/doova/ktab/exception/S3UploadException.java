package com.doova.ktab.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import org.springframework.http.HttpStatus;

public class S3UploadException extends KtabException {

    public S3UploadException(ApiMessageKey key) {
        super(key);
    }

    public S3UploadException(ApiMessageKey key, Throwable cause) {
        super(key, cause);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
