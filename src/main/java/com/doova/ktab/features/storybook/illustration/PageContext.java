package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.story.CharacterInScene;

import java.util.List;

public record PageContext(Long bookId, StorybookStatus status, Long pageId, int pageIndex, PageKind kind,
                          String sceneEn, TextZone textZone, List<CharacterInScene> cast, int pageGeneration,
                          int roundStartGeneration, boolean imageRowExists, String childSheetKey,
                          String companionSheetKey, ArtStyle style, boolean hijab,
                          String anchorKey, boolean glasses, String appearanceEn, String childClothing,
                          String companionEn, String styleNotes, List<SupportingLook> supporting) {

    /** A supporting character: its tag, description and outfit, and the sheet already drawn for it (null until then). */
    public record SupportingLook(String ref, String describeEn, String clothing, String sheetKey) {
    }

    public PageContext {
        supporting = supporting == null ? List.of() : List.copyOf(supporting);
    }

    public PageContext(Long bookId, StorybookStatus status, Long pageId, int pageIndex, PageKind kind,
                       String sceneEn, TextZone textZone, List<CharacterInScene> cast, int pageGeneration,
                       int roundStartGeneration, boolean imageRowExists, String childSheetKey,
                       String companionSheetKey, ArtStyle style, boolean hijab,
                       String anchorKey, boolean glasses, String appearanceEn, String childClothing,
                       String companionEn, String styleNotes) {
        this(bookId, status, pageId, pageIndex, kind, sceneEn, textZone, cast, pageGeneration, roundStartGeneration,
                imageRowExists, childSheetKey, companionSheetKey, style, hijab, anchorKey, glasses, appearanceEn, childClothing,
                companionEn, styleNotes, null);
    }


    public PageContext(Long bookId, StorybookStatus status, Long pageId, int pageIndex, PageKind kind,
                       String sceneEn, TextZone textZone, List<CharacterInScene> cast, int pageGeneration,
                       int roundStartGeneration, boolean imageRowExists, String childSheetKey,
                       String companionSheetKey, ArtStyle style, boolean hijab) {
        this(bookId, status, pageId, pageIndex, kind, sceneEn, textZone, cast, pageGeneration, roundStartGeneration,
                imageRowExists, childSheetKey, companionSheetKey, style, hijab, null, false, null, null, null, null, null);
    }
}
