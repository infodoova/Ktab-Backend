package com.doova.ktab.enums.book;

/**
 * Result of {@code PdfTypeClassifier} classification, run once per book before
 * any rendering or OCR happens. Determines the ingestion route (see {@link IngestionRoute}).
 * <p>
 * {@link #UNKNOWN} is the safe default: an unreadable, encrypted, or unclassifiable PDF
 * must never fall through to {@link #DIGITAL} — it routes to OCR.
 */
public enum PdfType {
    /** Text layer is authoritative and clean; no image-only pages. */
    DIGITAL,
    /** No usable text layer; pages are scanned images. */
    SCANNED,
    /** Scanned images with a foreign OCR text layer already burned in. */
    HYBRID_OCR,
    /** No single type dominates the sampled pages. */
    MIXED,
    /** Classification failed, timed out, or the PDF is encrypted/unreadable. */
    UNKNOWN
}
