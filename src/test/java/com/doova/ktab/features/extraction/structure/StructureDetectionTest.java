package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.ExtractionWarning;
import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.dto.WarningCode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.doova.ktab.features.extraction.ExtractionTestSupport.cleanedPages;
import static com.doova.ktab.features.extraction.ExtractionTestSupport.load;
import static org.assertj.core.api.Assertions.assertThat;

class StructureDetectionTest {

    // ---- embedded outline ----

    @Test
    void outlineGivesTitlesLevelsAndDestinationPages() throws Exception {
        try (PDDocument doc = load("book-outline.pdf")) {
            List<RawEntry> e = new EmbeddedOutlineExtractor().extract(doc);

            assertThat(e).extracting(RawEntry::title).containsExactly(
                    "المقدمة", "الفصل الأول", "المبحث الأول", "المبحث الثاني", "الفصل الثاني", "الخاتمة");
            assertThat(e).extracting(RawEntry::level).containsExactly(1, 1, 2, 2, 1, 1);
            assertThat(e).extracting(RawEntry::startPage).containsExactly(4, 6, 7, 8, 10, 13);
        }
    }

    @Test
    void aPdfWithoutAnOutlineGivesNothing() throws Exception {
        try (PDDocument doc = load("book-headings.pdf")) {
            assertThat(new EmbeddedOutlineExtractor().extract(doc)).isEmpty();
        }
    }

    // ---- printed TOC ----

    @Test
    void theNumberMayBeAtEitherEndOfTheLine() {
        assertThat(PrintedTocParser.parseLine("المقدمة ...................... ٥")).contains(new RawEntry("المقدمة", 1, null, 5));
        assertThat(PrintedTocParser.parseLine("١٢ . . . . . . . . . الفصل الأول")).contains(new RawEntry("الفصل الأول", 1, null, 12));
        assertThat(PrintedTocParser.parseLine("المبحث الأول ۱۷")).contains(new RawEntry("المبحث الأول", 2, null, 17));
        assertThat(PrintedTocParser.parseLine("٤٠ المطلب الأول")).contains(new RawEntry("المطلب الأول", 3, null, 40));
    }

    @Test
    void ordinaryTextAndBareNumbersAreNotTocLines() {
        assertThat(PrintedTocParser.parseLine("هذا سطر عادي بلا رقم")).isEmpty();
        assertThat(PrintedTocParser.parseLine("٣")).isEmpty();
        assertThat(PrintedTocParser.parseLine("ولد في عام ١٩٥٠ في مدينة صغيرة بالقرب من النهر")).isEmpty();
    }

    @Test
    void findsTheTocPageAndParsesItsEntriesInOrder() throws Exception {
        List<PageContent> pages = cleanedPages("book-printed-toc.pdf");

        List<Integer> tocPages = new PrintedTocDetector().findTocPages(pages);
        assertThat(tocPages).containsExactly(3);

        List<RawEntry> entries = new PrintedTocParser().parse(pages.subList(2, 3));
        assertThat(entries).extracting(RawEntry::title).containsExactly(
                "المقدمة", "الفصل الأول", "المبحث الأول", "المبحث الثاني", "الفصل الثاني", "الخاتمة");
        assertThat(entries).extracting(RawEntry::printedPage).containsExactly(1, 3, 4, 5, 7, 10);
        assertThat(entries).extracting(RawEntry::level).containsExactly(1, 1, 2, 2, 1, 1);
    }

    @Test
    void aBookWithoutAFihrisHasNoTocPages() throws Exception {
        assertThat(new PrintedTocDetector().findTocPages(cleanedPages("book-headings.pdf"))).isEmpty();
    }

    // ---- printed page -> PDF page ----

    @Test
    void theOffsetComesFromTitlesFoundInTheBodyAndIsValidated() throws Exception {
        List<PageContent> pages = cleanedPages("book-printed-toc.pdf");
        List<RawEntry> toc = new PrintedTocParser().parse(pages.subList(2, 3));

        PageNumberResolver.Resolution r = new PageNumberResolver().resolve(toc, pages);

        assertThat(r.offset()).isEqualTo(3);
        assertThat(r.entries()).extracting(RawEntry::startPage).containsExactly(4, 6, 7, 8, 10, 13);
        assertThat(r.confidence()).isGreaterThanOrEqualTo(0.8);
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void anEntryPastTheEndOfTheBookIsDroppedWithAWarning() throws Exception {
        List<PageContent> pages = cleanedPages("book-printed-toc.pdf");

        PageNumberResolver.Resolution r = new PageNumberResolver().resolve(
                List.of(new RawEntry("الفصل الأول", 1, null, 3), new RawEntry("ملحق", 1, null, 90)), pages);

        assertThat(r.warnings()).extracting(ExtractionWarning::code).contains(WarningCode.TOC_PAGE_OUT_OF_RANGE);
        assertThat(r.entries()).extracting(RawEntry::title).containsExactly("الفصل الأول");
    }

    @Test
    void anEntryWhoseTitleIsNotNearItsComputedPageKeepsThePageButWarns() throws Exception {
        List<PageContent> pages = cleanedPages("book-printed-toc.pdf");

        PageNumberResolver.Resolution r = new PageNumberResolver().resolve(
                List.of(new RawEntry("الفصل الأول", 1, null, 3), new RawEntry("المبحث الأول", 2, null, 4),
                        new RawEntry("عنوان غير موجود", 1, null, 5)), pages);

        assertThat(r.entries()).extracting(RawEntry::startPage).containsExactly(6, 7, 8);
        assertThat(r.warnings()).extracting(ExtractionWarning::code).containsExactly(WarningCode.TOC_PAGE_MISMATCH);
    }

    private static List<PageContent> bodyPages(int count, java.util.Map<Integer, String> headings) {
        List<PageContent> pages = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String body = headings.getOrDefault(i, "") + "نص الصفحة رقم " + i + " كلام كثير يملأ الصفحة.";
            pages.add(new PageContent(i, body, body, null, null, List.of(body.split("\n")), false));
        }
        return pages;
    }

    @Test
    void aTocTitleWithASubtitleMatchesTheBareHeadingInTheBody() {
        // real books: the فهرس says "الفصل الأول: شرق عدن" but the page heading is just "الفصل الأول"
        List<PageContent> pages = bodyPages(8, java.util.Map.of(3, "الجزء الأول\nالربيع\n", 4, "الفصل الأول\nشرق عدن\n",
                6, "الفصل الثاني\nالتشريفات\n"));
        List<RawEntry> toc = List.of(new RawEntry("الجزء الأول - الربيع", 1, null, 3),
                new RawEntry("الفصل الأول: شرق عدن", 1, null, 4), new RawEntry("الفصل الثاني: التشريفات", 1, null, 6));

        PageNumberResolver.Resolution r = new PageNumberResolver().resolve(toc, pages);

        assertThat(r.offset()).isZero();
        assertThat(r.entries()).extracting(RawEntry::startPage).containsExactly(3, 4, 6);
        assertThat(r.confidence()).isEqualTo(1.0);
        assertThat(r.warnings()).isEmpty();
        assertThat(r.entries().get(1).title()).isEqualTo("الفصل الأول: شرق عدن"); // the richer title is kept
    }

    @Test
    void theOffsetIsFoundWhenPrintedAndPdfPagesDifferAndTitlesHaveSubtitles() {
        List<PageContent> pages = bodyPages(10, java.util.Map.of(6, "الفصل الأول\nشرق عدن\n", 9, "الفصل الثاني\nالتشريفات\n"));

        PageNumberResolver.Resolution r = new PageNumberResolver().resolve(List.of(
                new RawEntry("الفصل الأول: شرق عدن", 1, null, 4), new RawEntry("الفصل الثاني: التشريفات", 1, null, 7)), pages);

        assertThat(r.offset()).isEqualTo(2);
        assertThat(r.entries()).extracting(RawEntry::startPage).containsExactly(6, 9);
        assertThat(r.warnings()).isEmpty();
    }

    // ---- headings ----

    @Test
    void headingsAreFoundButNotMentionsInsideParagraphs() throws Exception {
        List<RawEntry> h = new HeadingDetector().detect(cleanedPages("book-headings.pdf"));

        assertThat(h).extracting(RawEntry::title).containsExactly(
                "المقدمة", "الفصل الأول", "المبحث الأول", "المبحث الثاني", "الفصل الثاني", "الخاتمة");
        assertThat(h).extracting(RawEntry::startPage).containsExactly(4, 6, 7, 8, 10, 13);
        assertThat(h).extracting(RawEntry::level).containsExactly(1, 1, 2, 2, 1, 1);
    }
}
