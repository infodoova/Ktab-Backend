package com.doova.ktab.service.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * Vendor-neutral object storage access (S3/R2), independent of any single feature.
 * <p>
 * Extracted from {@code S3OcrStorageService} so that features outside {@code features.ocr}
 * (e.g. {@code features.studio}) can read/presign objects without depending on the OCR
 * feature package. {@code S3OcrStorageService} implements this in addition to its existing
 * OCR-specific {@code OcrStorageService} interface — there is still only one bean backing
 * both, so nothing about the underlying bucket/client changes.
 */
public interface ObjectStorageService {

    /**
     * Open a stream to read the object at {@code key}. Caller owns the stream and must close it.
     */
    InputStream getStream(String key);

    /**
     * Read the full object at {@code key} into memory. Use sparingly — prefer {@link #getStream}
     * or a presigned URL for anything that isn't small and short-lived.
     */
    byte[] getBytes(String key);

    /**
     * Presigned GET URL using the default expiration.
     */
    String generatePresignedUrl(String key);

    /**
     * Presigned GET URL valid for {@code expiration}.
     */
    String generatePresignedUrl(String key, Duration expiration);
}
