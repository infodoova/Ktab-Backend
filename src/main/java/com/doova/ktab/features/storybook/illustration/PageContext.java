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
                          String companionSheetKey, ArtStyle style, boolean hijab) {
}
