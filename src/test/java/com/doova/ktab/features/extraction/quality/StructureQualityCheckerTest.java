package com.doova.ktab.features.extraction.quality;

import com.doova.ktab.features.extraction.config.ExtractionProperties;
import com.doova.ktab.features.extraction.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.doova.ktab.features.extraction.ExtractionTestSupport.cleanedPages;
import static org.assertj.core.api.Assertions.assertThat;

class StructureQualityCheckerTest {

    private final StructureQualityChecker checker = new StructureQualityChecker(new ExtractionProperties());

    private static Chapter chapter(int index, String title, int start, int end, String text) {
        return new Chapter(index, title, start, end, TocEntryType.CHAPTER, text, List.of(), List.of());
    }

    @Test
    void flagsInvalidRangesDuplicatesEmptyAndShortChaptersAndLargeGaps() {
        List<Chapter> chapters = List.of(
                chapter(1, "الفصل الأول", 5, 4, "نص"),                    // invalid range
                chapter(2, "الفصل الأول", 6, 6, ""),                      // duplicate title and empty
                chapter(3, "الفصل الثالث", 7, 7, "قصير"),                 // short
                chapter(4, "الفصل الرابع", 8, 300, "نص طويل ".repeat(500))); // covers almost the whole book

        List<ExtractionWarning> w = checker.check(chapters, 300, 0.9);

        assertThat(w).extracting(ExtractionWarning::code).contains(WarningCode.INVALID_RANGE, WarningCode.DUPLICATE_HEADING,
                WarningCode.EMPTY_CHAPTER, WarningCode.SHORT_CHAPTER, WarningCode.LARGE_GAP);
        assertThat(w).filteredOn(x -> x.code() == WarningCode.INVALID_RANGE)
                .allSatisfy(x -> assertThat(x.severity()).isEqualTo(Severity.ERROR));
    }

    @Test
    void aSkippedChapterNumberIsReported() {
        List<ExtractionWarning> w = checker.check(List.of(
                chapter(1, "الفصل الأول", 1, 10, "نص ".repeat(200)),
                chapter(2, "الفصل الثالث", 11, 20, "نص ".repeat(200))), 20, 0.9);

        assertThat(w).extracting(ExtractionWarning::code).contains(WarningCode.MISSING_CHAPTERS);
    }

    @Test
    void aLowArabicRatioIsAnError() {
        assertThat(checker.check(List.of(), 10, 0.2))
                .anySatisfy(x -> assertThat(x).extracting(ExtractionWarning::code, ExtractionWarning::severity)
                        .containsExactly(WarningCode.LOW_ARABIC_RATIO, Severity.ERROR));
    }

    @Test
    void aGoodBookGetsNoWarnings() {
        assertThat(checker.check(List.of(
                chapter(1, "الفصل الأول", 1, 10, "نص ".repeat(200)),
                chapter(2, "الفصل الثاني", 11, 20, "نص ".repeat(200)),
                chapter(3, "الفصل الثالث", 21, 30, "نص ".repeat(200))), 30, 0.95)).isEmpty();
    }

    @Test
    void imageOnlyPagesAreAWarningUntilTheyAreMoreThanAFifthOfTheBook() throws Exception {
        assertThat(checker.checkPages(cleanedPages("book-hybrid.pdf")))
                .singleElement().satisfies(x -> {
                    assertThat(x.code()).isEqualTo(WarningCode.IMAGE_ONLY_PAGES);
                    assertThat(x.severity()).isEqualTo(Severity.WARNING);
                });

        List<PageContent> mostlyScanned = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            mostlyScanned.add(new PageContent(i, "", "", null, null, List.of(), i <= 4));
        }
        assertThat(checker.checkPages(mostlyScanned)).anySatisfy(x -> assertThat(x.severity()).isEqualTo(Severity.ERROR));
    }

    @Test
    void replacementCharactersAreReportedAsArtifacts() {
        List<PageContent> pages = List.of(new PageContent(1, "x", "ab��� cdefghij", null, null, List.of(), false));
        assertThat(checker.checkPages(pages)).extracting(ExtractionWarning::code).contains(WarningCode.EXTRACTION_ARTIFACTS);
    }
}
