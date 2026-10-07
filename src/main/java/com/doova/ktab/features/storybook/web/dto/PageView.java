package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TextZone;

public record PageView(int pageIndex, PageKind kind, String textAr, String sceneEn, TextZone textZone, String imageUrl) {

    public PageView(int pageIndex, PageKind kind, String textAr, TextZone textZone, String imageUrl) {
        this(pageIndex, kind, textAr, null, textZone, imageUrl);
    }
}
