package com.doova.ktab.features.extraction.dto;

import java.util.List;

public record BookExtractionResult(BookMetadata metadata, StructureDetection structureDetection, List<TocEntry> toc,
                                   List<Chapter> chapters, List<PageContent> pages, List<ExtractionWarning> warnings) {

    /** The structure and chapter text without the per-page text (what the endpoint returns unless asked for pages). */
    public BookExtractionResult compact() {
        return new BookExtractionResult(metadata, structureDetection, toc,
                chapters.stream().map(Chapter::withoutPages).toList(), List.of(), warnings);
    }
}
