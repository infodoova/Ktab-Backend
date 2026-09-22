package com.doova.ktab.features.ocr.batch;

import com.doova.ktab.enums.book.SpreadSide;

/**
 * Represents a page item read from the database manifest for OCR processing.
 */
public record PageItem(
        Long bookId,
        int pageNumber,
        String s3Key,
        String mime,
        String presignedUrl,
        int sourcePdfPage,
        SpreadSide spreadSide,
        int rotationDegrees
) {
    public PageItem(Long bookId, int pageNumber, String s3Key, String mime, String presignedUrl) {
        this(bookId, pageNumber, s3Key, mime, presignedUrl, pageNumber, SpreadSide.NONE, 0);
    }
}
