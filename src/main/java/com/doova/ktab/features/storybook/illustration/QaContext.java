package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.story.CharacterInScene;

import java.util.List;

public record QaContext(Long bookId, StorybookStatus status, Long pageId, Long imageId, PageImageStatus imageStatus,
                        String imageKey, String sceneEn, List<CharacterInScene> cast, String childSheetKey,
                        String companionSheetKey, ArtStyle style) {
}
