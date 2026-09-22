package com.doova.ktab.features.ocr.dto;

import com.doova.ktab.enums.book.ImageQuality;
import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.features.ocr.batch.OcrResult.DetectedHeading;

import java.util.ArrayList;
import java.util.List;

public record GeminiOcrResponse(
        PageKind pageKind,
        int orientation,
        ImageQuality imageQuality,
        boolean hasStamps,
        boolean hasHandwriting,
        int illegibleSegments,
        String printedPageLabel,
        String runningHeader,
        List<DetectedHeading> headings,
        String bodyMarkdown,
        String footnotesMarkdown,
        Boolean startsMidSentence,
        Boolean endsMidSentence,
        int wordCount,
        String finishReason
) {
    public GeminiOcrResponse(String markdown, int wordCount) {
        this(
                PageKind.BODY, 0, ImageQuality.GOOD, false, false, 0,
                "", "", List.of(), markdown, "", false, false, wordCount, "STOP"
        );
    }

    /**
     * Backward-compatibility accessor for markdown body.
     */
    public String markdown() {
        return bodyMarkdown != null ? bodyMarkdown : "";
    }

    public static GeminiOcrResponse empty() {
        return new GeminiOcrResponse(
                PageKind.BLANK, 0, ImageQuality.GOOD, false, false, 0,
                "", "", List.of(), "", "", false, false, 0, "STOP"
        );
    }

    public static GeminiOcrResponse fallback(String raw) {
        int words = raw.isBlank() ? 0 : raw.trim().split("\\s+").length;
        return new GeminiOcrResponse(
                PageKind.BODY, 0, ImageQuality.FAIR, false, false, 0,
                "", "", List.of(), raw.trim(), "", false, false, words, "FALLBACK"
        );
    }
}