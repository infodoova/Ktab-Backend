package com.doova.ktab.features.ocr.batch;

/**
 * Represents a page to be processed by OCR.
 * Uses presigned URL instead of byte[] to prevent OOM on large books.
 *
 * @param bookId       The book ID
 * @param pageNumber   The page number (1-indexed)
 * @param s3Key        The S3 object key
 * @param mime         The MIME type (image/png or image/jpeg)
 * @param presignedUrl Presigned URL for direct access (avoids loading bytes into heap)
 */
public record PageItem(
        Long bookId,
        int pageNumber,
        String s3Key,
        String mime,
        String presignedUrl
) {
    /**
     * Legacy constructor for backward compatibility during migration.
     * Converts byte[] to null presignedUrl - caller must handle.
     */
    @Deprecated
    public PageItem(Long bookId, int pageNumber, String s3Key, String mime, byte[] imageBytes) {
        this(bookId, pageNumber, s3Key, mime, (String) null);
    }
}
