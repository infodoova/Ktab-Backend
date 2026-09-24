package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;

import java.util.List;
import java.util.Map;

public record RenderContext(Long bookId, StorybookStatus status, String titleAr, String childNameAr, String dedication,
                            TashkeelLevel level, List<RenderModelFactory.PageSource> pages,
                            Map<Integer, String> imageKeysByPageIndex) {
}
