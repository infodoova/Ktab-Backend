package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.PaginationMode;
import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.model.book.BookPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TocAlignerTest {

    private TocAligner aligner;
    private OcrProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OcrProperties();
        aligner = new TocAligner(properties);
    }

    @Test
    @DisplayName("align should snap to detected heading within candidate search window")
    void align_candidateWithHeadingMatch_snapsToHeading() {
        List<BookPage> pages = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            BookPage p = new BookPage();
            p.setPageNumber(i);
            p.setPageKind(PageKind.BODY);
            p.setPrintedPageLabel(String.valueOf(i));
            if (i == 10) {
                p.setHeadings("[{\"text\": \"الفصل الأول: مدخل عام\", \"levelHint\": 1}]");
            }
            pages.add(p);
        }

        PageOffsetResolver resolver = new PageOffsetResolver(pages);

        RawToc rawToc = new RawToc(List.of(
                new RawToc.RawTocEntry("الفصل الأول", "الفصل", 1, 1, "9", SectionType.CHAPTER)
        ));

        // Printed page is 9, but heading is on page 10 (within search window of 2)
        List<TocAligner.AlignedSection> aligned = aligner.align(rawToc, pages, resolver, PaginationMode.PRINTED);

        assertEquals(1, aligned.size());
        assertEquals(10, aligned.getFirst().startPage());
        assertEquals("الفصل الأول: مدخل عام", aligned.getFirst().startAnchor());
        assertTrue(aligned.getFirst().confidence().doubleValue() >= 0.90);
        assertFalse(aligned.getFirst().needsReview());
    }

    @Test
    @DisplayName("align should fallback to sequence alignment when pagination is NONE")
    void align_nonePaginationMode_performsMonotonicSequenceAlignment() {
        List<BookPage> pages = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            BookPage p = new BookPage();
            p.setPageNumber(i);
            p.setPageKind(PageKind.BODY);
            p.setPrintedPageLabel(null); // No printed labels
            if (i == 3) {
                p.setHeadings("[{\"text\": \"الباب الأول\", \"levelHint\": 1}]");
            } else if (i == 7) {
                p.setHeadings("[{\"text\": \"الباب الثاني\", \"levelHint\": 1}]");
            }
            pages.add(p);
        }

        PageOffsetResolver resolver = new PageOffsetResolver(pages);

        RawToc rawToc = new RawToc(List.of(
                new RawToc.RawTocEntry("الباب الأول", "الباب", 1, 1, "", SectionType.PART),
                new RawToc.RawTocEntry("الباب الثاني", "الباب", 2, 1, "", SectionType.PART)
        ));

        List<TocAligner.AlignedSection> aligned = aligner.align(rawToc, pages, resolver, PaginationMode.NONE);

        assertEquals(2, aligned.size());
        assertEquals(3, aligned.get(0).startPage());
        assertEquals(7, aligned.get(1).startPage());
    }
}
