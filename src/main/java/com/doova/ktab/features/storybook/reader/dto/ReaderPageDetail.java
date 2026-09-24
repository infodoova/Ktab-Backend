package com.doova.ktab.features.storybook.reader.dto;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TextZone;

public record ReaderPageDetail(
        int pageIndex,
        PageKind kind,
        String textAr,
        TextZone textZone,
        String imageKey
) {
}
