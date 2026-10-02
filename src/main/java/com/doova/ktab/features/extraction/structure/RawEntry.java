package com.doova.ktab.features.extraction.structure;

/** One structure entry before normalization. {@code printedPage} is set only by the printed-TOC path. */
public record RawEntry(String title, int level, Integer startPage, Integer printedPage) {
}
