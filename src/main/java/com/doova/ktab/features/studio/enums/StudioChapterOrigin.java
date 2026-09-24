package com.doova.ktab.features.studio.enums;

/**
 * How a {@code tbl_studio_chapters} row's link to its {@code BookSection} was established.
 * Both are exact, at creation time — see docs/ocr_engine_v3.md, "Ktab never infers correspondence".
 */
public enum StudioChapterOrigin {
    /** Digital-PDF route: Studio parsed the chapter; we projected it into a new BookSection. */
    STUDIO_PROJECTED,
    /** OCR route: we created the chapter from an existing BookSection and pushed our own text. */
    KTAB_PUSHED
}
