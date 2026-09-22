package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.book.StructureStatus;
import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SectionTreeBuilderTest {

    private SectionTreeBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SectionTreeBuilder(new OcrProperties());
    }

    @Test
    @DisplayName("build should create FRONT_MATTER section if first content section starts after page 1")
    void build_firstSectionStartsAfterPageOne_createsFrontMatter() {
        Book book = new Book();
        book.setId(1L);

        List<TocAligner.AlignedSection> aligned = List.of(
                new TocAligner.AlignedSection("الباب الأول", "الباب", 1, 1, "10", SectionType.PART, 15, "الباب الأول", BigDecimal.valueOf(0.95), false),
                new TocAligner.AlignedSection("الفصل الأول", "الفصل", 1, 2, "12", SectionType.CHAPTER, 18, "الفصل الأول", BigDecimal.valueOf(0.95), false)
        );

        SectionTreeBuilder.TreeBuildResult result = builder.build(book, aligned, 50, StructureSource.TOC_VISION);

        assertEquals(3, result.sections().size());

        // First section is FRONT_MATTER from page 1 to 14
        BookSection frontMatter = result.sections().get(0);
        assertEquals(SectionType.FRONT_MATTER, frontMatter.getSectionType());
        assertEquals(1, frontMatter.getStartPage());
        assertEquals(14, frontMatter.getEndPage());

        // Second section is level 1
        BookSection part = result.sections().get(1);
        assertEquals("الباب الأول", part.getTitle());
        assertEquals(15, part.getStartPage());
        assertNull(part.getParent());

        // Third section is level 2 with parent = part
        BookSection chapter = result.sections().get(2);
        assertEquals("الفصل الأول", chapter.getTitle());
        assertEquals(part, chapter.getParent());
        assertEquals(18, chapter.getStartPage());
        assertEquals(50, chapter.getEndPage());

        assertEquals(StructureStatus.RESOLVED, result.status());
    }

    @Test
    @DisplayName("build should mark status as NEEDS_REVIEW if any section has low confidence")
    void build_lowConfidenceSection_marksNeedsReview() {
        Book book = new Book();
        book.setId(2L);

        List<TocAligner.AlignedSection> aligned = List.of(
                new TocAligner.AlignedSection("الباب الأول", "الباب", 1, 1, "1", SectionType.PART, 1, "الباب الأول", BigDecimal.valueOf(0.50), true)
        );

        SectionTreeBuilder.TreeBuildResult result = builder.build(book, aligned, 30, StructureSource.HEADINGS);
        assertEquals(StructureStatus.NEEDS_REVIEW, result.status());
    }
}
