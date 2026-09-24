package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.StorybookStatus;

public record SheetContext(Long bookId, StorybookStatus status, boolean storyApproved, ChildGender gender,
                           AgeBand ageBand, ChildAppearance appearance, CompanionSpec companion, ArtStyle style,
                           int childSheetVersion, String childSheetKey, String photoKey, boolean photoBased,
                           boolean companionSheetExists) {
}
