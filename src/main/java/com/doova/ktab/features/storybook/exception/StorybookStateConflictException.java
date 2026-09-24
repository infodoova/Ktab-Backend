package com.doova.ktab.features.storybook.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.springframework.http.HttpStatus;

/** The request is valid but the book is not in a state that allows it (e.g. approving twice). */
public class StorybookStateConflictException extends KtabException {

    public StorybookStateConflictException(ApiMessageKey key) {
        super(key);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
