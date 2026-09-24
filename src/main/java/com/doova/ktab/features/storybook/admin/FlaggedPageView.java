package com.doova.ktab.features.storybook.admin;

import java.util.List;

public record FlaggedPageView(Long bookId, Long pageId, int pageIndex, int generation, String imageUrl,
                              String sceneEn, List<String> problems) {
}
