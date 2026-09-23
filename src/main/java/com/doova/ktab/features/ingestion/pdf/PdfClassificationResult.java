package com.doova.ktab.features.ingestion.pdf;

import com.doova.ktab.enums.book.PdfType;
import lombok.Builder;
import lombok.Getter;

/**
 * Evidence + verdict from {@link PdfTypeClassifier}. Serialized as-is into
 * {@code tbl_books.col_pdf_classification} (JSONB) by the ingestion router, so a
 * misrouted book can be understood later without re-running the classifier against
 * a PDF that may have since been replaced. See docs/ocr_engine_v3.md, Phase 0 / 1.
 */
@Getter
@Builder
public class PdfClassificationResult {

    private final PdfType pdfType;

    /** Why UNKNOWN was returned, or null for a normal DIGITAL/SCANNED/HYBRID_OCR/MIXED result. */
    private final String reason;

    private final int totalPages;
    private final int sampledPages;
    private final int digitalPages;
    private final int scannedPages;
    private final int hybridPages;
    private final int blankPages;
    private final int errorPages;

    /** Ratios are against {@code classifiable} pages (digital+scanned+hybrid), not totalPages. */
    private final Double digitalRatio;
    private final Double scannedRatio;
    private final Double hybridRatio;

    public static PdfClassificationResult unknown(String reason, int sampledPages) {
        return PdfClassificationResult.builder()
                .pdfType(PdfType.UNKNOWN)
                .reason(reason)
                .sampledPages(sampledPages)
                .build();
    }
}
