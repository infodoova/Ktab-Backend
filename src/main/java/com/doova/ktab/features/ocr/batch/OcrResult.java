package com.doova.ktab.features.ocr.batch;

public record OcrResult(Long bookId, int pageNumber, String s3Key, String markdown, int wordCount) {
}
