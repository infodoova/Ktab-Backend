package com.doova.ktab.features.storybook.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookMessagesTest {

    @Test
    void everyStorybookKeyHasAMessage() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        Arrays.stream(ApiMessageKey.values())
                .filter(k -> k.name().startsWith("STORYBOOK_"))
                .forEach(k -> assertThat(source.getMessage(k.getKey(), null, Locale.ENGLISH))
                        .as(k.name()).isNotBlank());
        assertThat(Arrays.stream(ApiMessageKey.values()).filter(k -> k.name().startsWith("STORYBOOK_"))).hasSize(32);
    }

    @Test
    void stateConflictIs409() {
        assertThat(new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
