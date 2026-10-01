package com.doova.ktab.features.extraction.dto;

import java.util.List;

public record Section(String title, int level, int startPage, int endPage, TocEntryType type, List<Section> subsections) {
}
