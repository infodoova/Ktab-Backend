package com.doova.ktab.features.extraction.dto;

import java.util.List;

public record Chapter(int index, String title, int startPage, int endPage, TocEntryType type, String text,
                      List<PageContent> pages, List<Section> sections) {

    public Chapter withoutPages() {
        return new Chapter(index, title, startPage, endPage, type, text, List.of(), sections);
    }
}
