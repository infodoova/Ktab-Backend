package com.doova.ktab.features.extraction.dto;

import java.util.List;

/**
 * One physical PDF page. {@code rawText} is never overwritten; {@code cleanedText} has repeated headers/footers and the
 * standalone page number removed. {@code imageOnly} marks a page with no text but an image (a scanned page).
 */
public record PageContent(int pdfPage, String rawText, String cleanedText, String printedPageLabel,
                          String runningHeader, List<String> lines, boolean imageOnly) {
}
