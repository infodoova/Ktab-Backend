package com.doova.ktab.features.ocr.ai;

import com.doova.ktab.features.ocr.dto.GeminiOcrResponse;

public interface GeminiOcrService {

    /**
     * OCR a page using a presigned URL (memory-efficient).
     * Downloads image bytes on-demand and processes immediately.
     */
    GeminiOcrResponse ocrOnePageFromUrl(String presignedUrl, String mime);

    /**
     * OCR a page from raw bytes (original method).
     */
    GeminiOcrResponse ocrOnePage(byte[] imageBytes, String mime);
}
