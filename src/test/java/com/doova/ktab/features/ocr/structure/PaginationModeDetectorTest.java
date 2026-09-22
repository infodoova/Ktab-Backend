package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.PaginationMode;
import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.model.book.BookPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaginationModeDetectorTest {

    private PaginationModeDetector detector;

    @BeforeEach
    void setUp() {
        OcrProperties properties = new OcrProperties();
        detector = new PaginationModeDetector(properties);
    }

    @Test
    @DisplayName("detect should return PRINTED when >= 70% of body pages have numeric labels")
    void detect_highNumericRatio_returnsPrinted() {
        List<BookPage> pages = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            BookPage p = new BookPage();
            p.setPageNumber(i);
            p.setPageKind(PageKind.BODY);
            // 8 out of 10 pages have printed numbers (80% >= 70%)
            p.setPrintedPageLabel(i <= 8 ? String.valueOf(i) : null);
            pages.add(p);
        }

        assertEquals(PaginationMode.PRINTED, detector.detect(pages));
    }

    @Test
    @DisplayName("detect should return PARTIAL when 20% to 70% of body pages have numeric labels")
    void detect_mediumNumericRatio_returnsPartial() {
        List<BookPage> pages = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            BookPage p = new BookPage();
            p.setPageNumber(i);
            p.setPageKind(PageKind.BODY);
            // 4 out of 10 pages have printed numbers (40%)
            p.setPrintedPageLabel(i <= 4 ? String.valueOf(i) : null);
            pages.add(p);
        }

        assertEquals(PaginationMode.PARTIAL, detector.detect(pages));
    }

    @Test
    @DisplayName("detect should return NONE when < 20% of body pages have numeric labels")
    void detect_lowNumericRatio_returnsNone() {
        List<BookPage> pages = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            BookPage p = new BookPage();
            p.setPageNumber(i);
            p.setPageKind(PageKind.BODY);
            // 1 out of 10 pages has a printed number (10% < 20%)
            p.setPrintedPageLabel(i == 1 ? "1" : null);
            pages.add(p);
        }

        assertEquals(PaginationMode.NONE, detector.detect(pages));
    }
}
