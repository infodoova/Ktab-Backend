package com.doova.ktab.enums.book;

public enum PageKind {
    COVER,
    TITLE_PAGE,
    COPYRIGHT,
    BLANK,
    TOC,
    BODY,
    INDEX,
    IMAGE_ONLY,
    SPREAD,       // Model detected two pages in one image -> triggers split + re-OCR
    UNREADABLE,   // Rotated / illegible -> triggers rotation retry
    OTHER,
    UNKNOWN
}
