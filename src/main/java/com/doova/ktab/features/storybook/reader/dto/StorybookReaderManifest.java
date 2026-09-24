package com.doova.ktab.features.storybook.reader.dto;

import com.doova.ktab.features.storybook.enums.StorybookStatus;

import java.util.List;

public record StorybookReaderManifest(
        Long bookId,
        StorybookStatus status,
        String titleAr,
        String childNameAr,
        int pageCount,
        List<ReaderPageDetail> pages
) {
}
