package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.PaginationMode;
import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.features.ocr.text.PageLabelParser;
import com.doova.ktab.model.book.BookPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaginationModeDetector {

    private final OcrProperties properties;

    /**
     * Determines whether the book has PRINTED, PARTIAL, or NONE pagination mode
     * based on the percentage of BODY pages with parseable numeric labels.
     */
    public PaginationMode detect(List<BookPage> pages) {
        if (pages == null || pages.isEmpty()) {
            return PaginationMode.NONE;
        }

        long bodyCount = 0;
        long numericCount = 0;

        for (BookPage page : pages) {
            // Check body pages
            if (page.getPageKind() == PageKind.BODY || page.getPageKind() == PageKind.UNKNOWN) {
                bodyCount++;
                if (PageLabelParser.parseNumeric(page.getPrintedPageLabel()).isPresent()) {
                    numericCount++;
                }
            }
        }

        if (bodyCount == 0) {
            return PaginationMode.NONE;
        }

        double ratio = (double) numericCount / bodyCount;

        double printedMinRatio = properties.getStructure().getPaginationPrintedMinRatio();
        double noneMaxRatio = properties.getStructure().getPaginationNoneMaxRatio();

        if (ratio >= printedMinRatio) {
            return PaginationMode.PRINTED;
        } else if (ratio < noneMaxRatio) {
            return PaginationMode.NONE;
        } else {
            return PaginationMode.PARTIAL;
        }
    }
}
