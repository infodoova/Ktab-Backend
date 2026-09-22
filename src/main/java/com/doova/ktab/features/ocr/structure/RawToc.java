package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.SectionType;

import java.util.List;

public record RawToc(List<RawTocEntry> entries) {

    public record RawTocEntry(
            String title,
            String divisionLabel,
            Integer ordinal,
            int level,
            String printedPageLabel,
            SectionType sectionType,
            Integer targetPdfPage // Set when extracted directly from PDF outline bookmarks
    ) {
        public RawTocEntry(String title, String divisionLabel, Integer ordinal, int level, String printedPageLabel, SectionType sectionType) {
            this(title, divisionLabel, ordinal, level, printedPageLabel, sectionType, null);
        }
    }
}
