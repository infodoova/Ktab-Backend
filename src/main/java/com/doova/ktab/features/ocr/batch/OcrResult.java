package com.doova.ktab.features.ocr.batch;

import com.doova.ktab.enums.book.ImageQuality;
import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.SpreadSide;
import com.doova.ktab.enums.status.OcrStatus;

import java.util.List;

public record OcrResult(
        Long bookId,
        int pageNumber,
        String s3Key,
        int sourcePdfPage,
        SpreadSide spreadSide,
        int rotationDegrees,
        PageKind pageKind,
        ImageQuality imageQuality,
        int illegibleSegments,
        String printedPageLabel,
        String runningHeader,
        List<DetectedHeading> headings,
        String bodyMarkdown,
        String footnotesMarkdown,
        Boolean startsMidSentence,
        Boolean endsMidSentence,
        int wordCount,
        OcrStatus status,
        List<String> qualityFlags,
        String model,
        String promptVersion
) {
    public record DetectedHeading(String text, int levelHint) {}
}
