package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.model.book.BookPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PageOffsetResolverTest {

    @Test
    @DisplayName("toPageIndex should resolve constant offset accurately")
    void toPageIndex_constantOffset_resolvesAccurately() {
        List<BookPage> pages = new ArrayList<>();
        // 10 body pages starting at book page 11 with printed labels 1..10 (offset = 10)
        for (int i = 1; i <= 10; i++) {
            BookPage p = new BookPage();
            p.setPageNumber(10 + i);
            p.setPageKind(PageKind.BODY);
            p.setPrintedPageLabel(String.valueOf(i));
            pages.add(p);
        }

        PageOffsetResolver resolver = new PageOffsetResolver(pages);

        assertEquals(Optional.of(15), resolver.toPageIndex(5));
        assertEquals(Optional.of(20), resolver.toPageIndex(10));
    }

    @Test
    @DisplayName("toPageIndex should reject single OCR misread outlier via majority vote")
    void toPageIndex_singleMisreadOutlier_rejectsOutlier() {
        List<BookPage> pages = new ArrayList<>();
        // Offset is consistently 10, but page 15 has a corrupted label "99"
        for (int i = 1; i <= 10; i++) {
            BookPage p = new BookPage();
            p.setPageNumber(10 + i);
            p.setPageKind(PageKind.BODY);
            p.setPrintedPageLabel(i == 5 ? "99" : String.valueOf(i));
            pages.add(p);
        }

        PageOffsetResolver resolver = new PageOffsetResolver(pages);

        // Printed page 5 should resolve to 15 (offset 10), not 105
        assertEquals(Optional.of(15), resolver.toPageIndex(5));
    }

    @Test
    @DisplayName("toPageIndex should handle offset jump due to inserted plates")
    void toPageIndex_offsetJump_handlesMultipleRuns() {
        List<BookPage> pages = new ArrayList<>();
        // First run: printed 1..5 at book pages 11..15 (offset 10)
        for (int i = 1; i <= 5; i++) {
            BookPage p = new BookPage();
            p.setPageNumber(10 + i);
            p.setPageKind(PageKind.BODY);
            p.setPrintedPageLabel(String.valueOf(i));
            pages.add(p);
        }
        // Plate jump of 4 unnumbered pages at 16, 17, 18, 19
        // Second run: printed 6..10 at book pages 20..24 (offset 14)
        for (int i = 6; i <= 10; i++) {
            BookPage p = new BookPage();
            p.setPageNumber(14 + i);
            p.setPageKind(PageKind.BODY);
            p.setPrintedPageLabel(String.valueOf(i));
            pages.add(p);
        }

        PageOffsetResolver resolver = new PageOffsetResolver(pages);

        assertEquals(Optional.of(13), resolver.toPageIndex(3)); // 3 + 10 = 13
        assertEquals(Optional.of(22), resolver.toPageIndex(8)); // 8 + 14 = 22
    }

    @Test
    @DisplayName("toPageIndex should return empty when no numeric labels are present")
    void toPageIndex_noNumericLabels_returnsEmpty() {
        List<BookPage> pages = new ArrayList<>();
        BookPage p = new BookPage();
        p.setPageNumber(1);
        p.setPageKind(PageKind.BODY);
        p.setPrintedPageLabel(null);
        pages.add(p);

        PageOffsetResolver resolver = new PageOffsetResolver(pages);
        assertTrue(resolver.toPageIndex(10).isEmpty());
    }
}
