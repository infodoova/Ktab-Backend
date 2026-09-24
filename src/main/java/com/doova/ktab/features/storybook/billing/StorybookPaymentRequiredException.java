package com.doova.ktab.features.storybook.billing;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.springframework.http.HttpStatus;

public class StorybookPaymentRequiredException extends KtabException {

    public StorybookPaymentRequiredException() {
        super(ApiMessageKey.STORYBOOK_INSUFFICIENT_CREDITS);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.PAYMENT_REQUIRED;
    }
}
