package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.TextZone;

import java.util.List;

public record ReaderManifest(Long bookId, String dir, String titleAr, List<Page> pages) {
    public record Page(int order, RenderPage.Kind kind, String textAr, TextZone textZone, String imageUrl) {
    }
}
