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
        @NotNull Long childProfileId,
        @NotBlank String blueprintKey,
        @Size(max = 3) List<Interest> interests,
        @Valid CompanionSpec companion,
        StorySetting setting,
        @NotNull ArtStyle style,
        @NotNull Integer pageCount,
        LanguageVariety variety,
        TashkeelLevel tashkeelLevel,
        @Size(max = 300) String dedication
) {
}
