package com.doova.ktab.features.extraction.dto;

/** How the structure was found. (Distinct from com.doova.ktab.enums.book.StructureSource, which is what gets stored.) */
public enum DetectionSource {
    EMBEDDED_OUTLINE, PRINTED_TOC, HEADING_DETECTION, NONE
}
