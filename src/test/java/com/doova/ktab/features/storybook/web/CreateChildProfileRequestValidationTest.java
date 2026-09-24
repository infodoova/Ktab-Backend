package com.doova.ktab.features.storybook.web;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CreateChildProfileRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateChildProfileRequest request(String name) {
        return new CreateChildProfileRequest(name, ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"محمد", "مُحَمَّد", "عبد الله", "آدم", "فاطمة"})
    void acceptsArabicNamesWithOrWithoutTashkeel(String name) {
        assertThat(validator.validate(request(name))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mohamed", "محمد2", "م", "محـمد", "محمد!", "   "})
    void rejectsLatinDigitsTatweelPunctuationAndTooShort(String name) {
        assertThat(validator.validate(request(name))).isNotEmpty();
    }
}
