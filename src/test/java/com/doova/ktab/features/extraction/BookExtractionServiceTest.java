package com.doova.ktab.features.extraction;

import com.doova.ktab.features.extraction.config.ExtractionProperties;
import com.doova.ktab.features.extraction.dto.*;
import com.doova.ktab.features.extraction.pdf.*;
import com.doova.ktab.features.extraction.quality.StructureQualityChecker;
import com.doova.ktab.features.extraction.structure.*;
import com.doova.ktab.features.extraction.text.ArabicTextCleaner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.doova.ktab.features.extraction.ExtractionTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class BookExtractionServiceTest {

    private final BookExtractionService service = new BookExtractionService(
            new PdfValidationService(200L * 1024 * 1024), new PdfMetadataExtractor(), new PdfPageExtractor(),
            new ArabicTextCleaner(),
            new BookStructureExtractor(new EmbeddedOutlineExtractor(), new PrintedTocDetector(), new PrintedTocParser(),
                    new PageNumberResolver(), new HeadingDetector()),
            new TocNormalizer(), new ChapterBuilder(), new StructureQualityChecker(new ExtractionProperties()));

    @Test
    void theEmbeddedOutlineIsPreferredAndGivesFullConfidence() {
        BookExtractionResult r = service.extract(fixture("book-outline.pdf"));
        assertThat(r.structureDetection()).isEqualTo(new StructureDetection(DetectionSource.EMBEDDED_OUTLINE, 1.0));
        assertThat(r.metadata().title()).isEqualTo("كتاب تجريبي");
    }

    @Test
    void thePrintedTocIsUsedWhenThereIsNoOutline() {
        BookExtractionResult r = service.extract(fixture("book-printed-toc.pdf"));
        assertThat(r.structureDetection().source()).isEqualTo(DetectionSource.PRINTED_TOC);
        assertThat(r.structureDetection().confidence()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void headingsAreTheLastResort() {
        BookExtractionResult r = service.extract(fixture("book-headings.pdf"));
        assertThat(r.structureDetection().source()).isEqualTo(DetectionSource.HEADING_DETECTION);
        assertThat(r.structureDetection().confidence()).isBetween(0.5, 0.8);
    }

    @Test
    void hierarchyAndRangesAreTheSameForEverySource() {
        for (String f : List.of("book-outline.pdf", "book-printed-toc.pdf", "book-headings.pdf")) {
            BookExtractionResult r = service.extract(fixture(f));

            assertThat(r.toc()).as(f).extracting(TocEntry::title)
                    .containsExactly("المقدمة", "الفصل الأول", "الفصل الثاني", "الخاتمة");
            assertThat(r.toc().get(1).children()).extracting(TocEntry::title).containsExactly("المبحث الأول", "المبحث الثاني");
            assertThat(r.toc().get(1).children()).extracting(TocEntry::type).containsOnly(TocEntryType.SECTION);
            assertThat(r.chapters()).as(f).extracting(Chapter::startPage, Chapter::endPage)
                    .containsExactly(tuple(4, 5), tuple(6, 9), tuple(10, 12), tuple(13, 14));
            assertThat(r.chapters()).extracting(Chapter::type).containsExactly(
                    TocEntryType.INTRODUCTION, TocEntryType.CHAPTER, TocEntryType.CHAPTER, TocEntryType.CONCLUSION);
            assertThat(r.chapters()).extracting(Chapter::index).containsExactly(1, 2, 3, 4);
            assertThat(r.chapters().get(1).sections()).extracting(Section::startPage, Section::endPage)
                    .containsExactly(tuple(7, 7), tuple(8, 9));
            assertThat(r.chapters().get(1).text()).contains("الفصل الأول").doesNotContain("اسم الكتاب");
            assertThat(r.chapters().get(1).pages()).extracting(PageContent::pdfPage).containsExactly(6, 7, 8, 9);
        }
    }

    @Test
    void pagesBeforeTheFirstEntryAreKeptButAreNotAChapter() {
        BookExtractionResult r = service.extract(fixture("book-outline.pdf"));
        assertThat(r.pages()).hasSize(14);
        assertThat(r.pages().get(0).rawText()).contains("كتاب تجريبي");
        assertThat(r.chapters().get(0).startPage()).isEqualTo(4);
    }

    @Test
    void aCleanBookHasNoErrorWarnings() {
        BookExtractionResult r = service.extract(fixture("book-outline.pdf"));
        assertThat(r.warnings()).noneMatch(w -> w.severity() == Severity.ERROR);
    }

    @Test
    void imageOnlyPagesInAHybridBookAreReportedNotHidden() {
        BookExtractionResult r = service.extract(fixture("book-hybrid.pdf"));
        assertThat(r.warnings()).anySatisfy(w -> {
            assertThat(w.code()).isEqualTo(WarningCode.IMAGE_ONLY_PAGES);
            assertThat(w.severity()).isEqualTo(Severity.WARNING);
            assertThat(w.message()).contains("7").contains("11");
        });
    }

    @Test
    void anEncryptedPdfIsRejectedNotExtracted() {
        assertThatThrownBy(() -> service.extract(fixture("encrypted.pdf")))
                .isInstanceOf(PdfRejectedException.class)
                .hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.ENCRYPTED);
    }

    @Test
    void compactDropsPageTextButKeepsTheStructure() {
        BookExtractionResult r = service.extract(fixture("book-outline.pdf")).compact();
        assertThat(r.pages()).isEmpty();
        assertThat(r.chapters()).hasSize(4);
        assertThat(r.chapters().get(1).pages()).isEmpty();
        assertThat(r.chapters().get(1).text()).contains("الفصل الأول");
    }
}
