package com.doova.ktab.features.storybook.prompt;

import com.doova.ktab.features.storybook.enums.LanguageVariety;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLibraryTest {

    private final PromptLibrary library = new PromptLibrary();

    @ParameterizedTest
    @ValueSource(strings = {"story-plan-system", "page-rewrite-system", "critic-system",
            "moderation-system", "visual-qa-system"})
    void everyPromptExistsAndIsNotEmpty(String name) {
        assertThat(library.get(name)).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(value = LanguageVariety.class, names = {"LEBANESE", "EGYPTIAN", "GULF"})
    void everyDialectHasAGuideWithItsNoTashkeelRule(LanguageVariety variety) {
        assertThat(library.dialectGuide(variety)).contains("No tashkeel");
    }

    @Test
    void msaHasNoDialectGuide() {
        assertThat(library.dialectGuide(LanguageVariety.MSA)).isEmpty();
    }

    @Test
    void missingPromptIsAnError() {
        assertThatThrownBy(() -> library.get("does-not-exist")).isInstanceOf(IllegalStateException.class);
    }
}
