package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStorybookRequest(
        Long childProfileId,
        String blueprintKey,
        @Size(max = 3) List<Interest> interests,
        @Valid CompanionSpec companion,
        StorySetting setting,
        @NotNull ArtStyle style,
        @NotNull Integer pageCount,
        LanguageVariety variety,
        TashkeelLevel tashkeelLevel,
        @Size(max = 300) String dedication,
        String theme,
        String storyTone,
        String lesson,
        String storyIdea,
        List<String> thingsToAvoid,
        String orientation,
        @Valid List<CharacterInput> characters
) {
    public CreateStorybookRequest(
            Long childProfileId,
            String blueprintKey,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting,
            ArtStyle style,
            Integer pageCount,
            LanguageVariety variety,
            TashkeelLevel tashkeelLevel,
            String dedication
    ) {
        this(childProfileId, blueprintKey, interests, companion, setting, style, pageCount, variety,
                tashkeelLevel, dedication, null, null, null, null, null, null, null);
    }
}
