package com.doova.ktab.enums;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

public enum ErrorMessage {

    USERID_ALREADY_EXISTS("user.already.exists"), WRONG_CREDENTIALS("wrong.credentials"), USER_NOT_FOUND("user.not.found"), USER_ID_SHOULD_NOT_BE_EMPTY("user.id.should.not.be.empty"), ID_SHOULD_NOT_BE_EMPTY("id.should.not.be.empty");

    private final String key;

    // Constructor
    ErrorMessage(String key) {
        this.key = key;
    }

    // Method to get localized message
    public String getMessage(MessageSource messageSource, Object... args) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
