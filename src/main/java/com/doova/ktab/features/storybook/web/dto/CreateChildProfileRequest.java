package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateChildProfileRequest(
        @NotBlank @Pattern(regexp = CreateChildProfileRequest.ARABIC_NAME) String nameAr,
        @NotNull ChildGender gender,
        @NotNull AgeBand ageBand,
        @NotNull @Valid ChildAppearance appearance
) {
    /**
     * Arabic letters (U+0621-U+063A, U+0641-U+064A, U+0671-U+06D3), tashkeel (U+064B-U+0652, U+0670)
     * and spaces; 2 to 30 characters after trimming. Excludes tatweel (U+0640) and digits.
     */
    public static final String ARABIC_NAME =
            "^\\s*[\\u0621-\\u063A\\u0641-\\u064A\\u0671-\\u06D3][\\u0621-\\u063A\\u0641-\\u064A\\u064B-\\u0652\\u0670\\u0671-\\u06D3 ]{1,29}\\s*$";
}
