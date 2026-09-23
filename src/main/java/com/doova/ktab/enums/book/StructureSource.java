package com.doova.ktab.enums.book;

public enum StructureSource {
    PDF_OUTLINE,
    TEXT_LAYER,
    TOC_VISION,
    HEADINGS,
    MANUAL,
    /** Section was projected verbatim from an ElevenLabs Studio chapter; see docs/ocr_engine_v3.md. */
    STUDIO
}
