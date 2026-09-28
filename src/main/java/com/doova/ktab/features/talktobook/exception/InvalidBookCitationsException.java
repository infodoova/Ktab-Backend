package com.doova.ktab.features.talktobook.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.springframework.http.HttpStatus;

/** Signals an upstream answer that cannot be returned or cached without valid evidence. */
public class InvalidBookCitationsException extends KtabException {
    public InvalidBookCitationsException() {
        super(ApiMessageKey.INTERNAL_ERROR);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.BAD_GATEWAY;
    }
}
