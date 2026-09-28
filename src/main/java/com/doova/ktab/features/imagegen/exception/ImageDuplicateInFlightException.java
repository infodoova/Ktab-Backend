package com.doova.ktab.features.imagegen.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.springframework.http.HttpStatus;

public class ImageDuplicateInFlightException extends KtabException {

    public ImageDuplicateInFlightException(ApiMessageKey messageKey) {
        super(messageKey);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
