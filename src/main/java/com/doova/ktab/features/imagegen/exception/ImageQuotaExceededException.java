package com.doova.ktab.features.imagegen.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.springframework.http.HttpStatus;

public class ImageQuotaExceededException extends KtabException {

    public ImageQuotaExceededException(ApiMessageKey messageKey) {
        super(messageKey);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
