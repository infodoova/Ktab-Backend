package com.doova.ktab.features.extraction.dto;

import java.util.List;

public record TocEntry(String title, int level, int startPage, int endPage, TocEntryType type, List<TocEntry> children) {
}
