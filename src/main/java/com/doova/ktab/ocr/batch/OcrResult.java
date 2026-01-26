package com.doova.ktab.ocr.batch;

public record OcrResult(Long bookId, int pageNumber, String s3Key, String markdown, int wordCount) {
}
