package com.doova.ktab.enums.book;

/**
 * Which pipeline owns text and structure extraction for a book.
 * <p>
 * The two pipelines ({@code ocrJob} and {@code studioIngestionJob}) are independent:
 * neither pipeline's code imports the other's. Both write to the same shared content
 * tables ({@code tbl_book_pages}, {@code tbl_book_sections}), which is the only contract
 * between them. See docs/ocr_engine_v3.md.
 */
public enum IngestionRoute {
    /** ElevenLabs Studio parses the PDF; Ktab adopts its chapter structure verbatim. */
    STUDIO,
    /** Ktab's own Gemini-based OCR pipeline (v2) renders and reads every page. */
    OCR,
    /** Ktab's own text-layer extraction (features.extraction): DIGITAL books while Studio is off, and every book while OCR is off. */
    NATIVE
}
