package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.StorybookStatus;

import java.util.List;

public record SheetContext(Long bookId, StorybookStatus status, boolean storyApproved, ChildGender gender,
                           AgeBand ageBand, ChildAppearance appearance, CompanionSpec companion, ArtStyle style,
                           int childSheetVersion, String childSheetKey, String photoKey, boolean photoBased,
                           boolean companionSheetExists, String childClothing, List<SupportingSheet> supporting) {

    /** One supporting character: its tag, its description for the picture model, and its sheet if one was already drawn. */
    public record SupportingSheet(String id, String ref, String describeEn, String clothing, String sheetKey) {
    }

    public SheetContext {
        supporting = supporting == null ? List.of() : List.copyOf(supporting);
    }

    public SheetContext(Long bookId, StorybookStatus status, boolean storyApproved, ChildGender gender,
                        AgeBand ageBand, ChildAppearance appearance, CompanionSpec companion, ArtStyle style,
                        int childSheetVersion, String childSheetKey, String photoKey, boolean photoBased,
                        boolean companionSheetExists, String childClothing) {
        this(bookId, status, storyApproved, gender, ageBand, appearance, companion, style, childSheetVersion,
                childSheetKey, photoKey, photoBased, companionSheetExists, childClothing, null);
    }


    public SheetContext(Long bookId, StorybookStatus status, boolean storyApproved, ChildGender gender,
                        AgeBand ageBand, ChildAppearance appearance, CompanionSpec companion, ArtStyle style,
                        int childSheetVersion, String childSheetKey, String photoKey, boolean photoBased,
                        boolean companionSheetExists) {
        this(bookId, status, storyApproved, gender, ageBand, appearance, companion, style, childSheetVersion,
                childSheetKey, photoKey, photoBased, companionSheetExists, null);
    }
}
