package com.doova.ktab.features.extraction.pdf;

import com.doova.ktab.features.extraction.dto.BookMetadata;
import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.text.ArabicTextCleaner;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.doova.ktab.features.extraction.ExtractionTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class PdfPageExtractorAndCleanerTest {

    @Test
    void readsTheMetadataThePdfCarries() throws Exception {
        try (PDDocument doc = load("book-headings.pdf")) {
            BookMetadata m = new PdfMetadataExtractor().extract(doc);
            assertThat(m.title()).isEqualTo("كتاب تجريبي");
            assertThat(m.author()).isEqualTo("مؤلف تجريبي");
            assertThat(m.subject()).isEqualTo("اختبار الاستخراج");
            assertThat(m.language()).isEqualTo("ar");
            assertThat(m.pageCount()).isEqualTo(14);
            assertThat(m.keywords()).isNull();
        }
    }

    @Test
    void extractsEveryPageWithItsPdfNumberAndKeepsTheRawText() throws Exception {
        List<PageContent> pages = extract("book-headings.pdf");

        assertThat(pages).hasSize(14);
        assertThat(pages).extracting(PageContent::pdfPage).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
        assertThat(pages.get(5).rawText()).contains("الفصل الأول").contains("اسم الكتاب"); // header still in the raw text
        assertThat(pages.get(5).lines()).isNotEmpty();
        assertThat(pages.get(5).imageOnly()).isFalse();
    }

    @Test
    void cleaningRemovesTheRepeatedHeaderAndPageNumber() throws Exception {
        List<PageContent> pages = cleanedPages("book-headings.pdf");

        PageContent p6 = pages.get(5);
        assertThat(p6.cleanedText()).doesNotContain("اسم الكتاب").contains("الفصل الأول");
        assertThat(p6.runningHeader()).isEqualTo("اسم الكتاب");
        assertThat(p6.printedPageLabel()).isEqualTo("٣");
        assertThat(p6.cleanedText().lines()).noneMatch(l -> l.strip().equals("٣"));
        assertThat(p6.rawText()).contains("اسم الكتاب"); // raw is never overwritten
    }

    @Test
    void frontMatterPagesWithNoHeaderKeepTheirText() throws Exception {
        List<PageContent> pages = cleanedPages("book-headings.pdf");

        assertThat(pages.get(0).cleanedText()).contains("كتاب تجريبي").contains("مؤلف تجريبي");
        assertThat(pages.get(0).runningHeader()).isNull();
        assertThat(pages.get(2).cleanedText()).contains("إهداء");
    }

    @Test
    void imageOnlyPagesAreKeptEmptyAndFlagged() throws Exception {
        List<PageContent> pages = cleanedPages("book-hybrid.pdf");

        assertThat(pages).hasSize(14);
        assertThat(pages.get(6).imageOnly()).isTrue();   // pdf page 7
        assertThat(pages.get(10).imageOnly()).isTrue();  // pdf page 11
        assertThat(pages.get(6).cleanedText()).isEmpty();
        assertThat(pages.get(5).imageOnly()).isFalse();
    }

    @Test
    void cleaningNeverChangesLettersOrTashkeel() {
        String withTashkeel = "كَتَبَ الطَّالِبُ الدَّرْسَ";
        assertThat(ArabicTextCleaner.collapseWhitespace("  " + withTashkeel + "   \n\n\n\n")).isEqualTo(withTashkeel);
        assertThat(ArabicTextCleaner.collapseWhitespace("أ  إ   آ\n\n\n\nة ى ؤ ئ")).isEqualTo("أ إ آ\n\nة ى ؤ ئ");
    }

    @Test
    void artifactLinesAreRemovedButRealTextIsNot() {
        assertThat(ArabicTextCleaner.isArtifactLine("���")).isTrue();
        assertThat(ArabicTextCleaner.isArtifactLine("........")).isTrue();
        assertThat(ArabicTextCleaner.isArtifactLine("الفصل الأول")).isFalse();
        assertThat(ArabicTextCleaner.isArtifactLine("١٢")).isFalse();
    }
}
