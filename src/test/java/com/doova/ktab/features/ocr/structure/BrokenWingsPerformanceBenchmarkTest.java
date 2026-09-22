package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PaginationMode;
import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.book.StructureStatus;
import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.features.ocr.harmonize.HarmonizationGuard;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class BrokenWingsPerformanceBenchmarkTest {

    private OcrProperties properties;
    private PaginationModeDetector paginationDetector;
    private TocAligner tocAligner;
    private SectionTreeBuilder sectionTreeBuilder;
    private HarmonizationGuard harmonizationGuard;

    // Real Table of Contents from Page 54 of Gibran Khalil Gibran's "الأجنحة المتكسرة"
    private static final String TOC_PAGE_54 = """
            الفهرس
            جبران خليل جبران
            الجنحة المتكسرة ـ 1 ـ توطئة\s
            **************
            1\s
            1 ...................................................................................................................................... توطئة\s
            3 ......................................................................................................................... الكآبة الخرساء -\s
            5 ............................................................................................................................... يد القضاء -\s
            8 ......................................................................................................................... في باب الهيكل -\s
            10 ........................................................................................................................ الشعلة البيضاء -\s
            12 ................................................................................................................................ العاصفة -\s
            19 ............................................................................................................................ بحيرة النار -\s
            33 ................................................................................................................... أمام عرش الموت -\s
            41 ............................................................................................................. بين عشتروت والمسيح -\s
            44 ............................................................................................................................... التضحية -\s
            50 ................................................................................................................................... المنقذ -\s
            54\s
            """;

    @BeforeEach
    void setUp() {
        properties = new OcrProperties();
        paginationDetector = new PaginationModeDetector(properties);
        tocAligner = new TocAligner(properties);
        sectionTreeBuilder = new SectionTreeBuilder(properties);
        harmonizationGuard = new HarmonizationGuard();
    }

    @Test
    @DisplayName("Performance & Accuracy Benchmark on 54-page book: الأجنحة المتكسرة")
    void benchmark_brokenWingsFullPipeline() {
        long startTime = System.nanoTime();

        // 1. Simulate 54 extracted OCR pages
        List<BookPage> pages = build54Pages();
        assertEquals(54, pages.size());

        // 2. Pagination Mode Detection
        PaginationMode mode = paginationDetector.detect(pages);
        assertEquals(PaginationMode.PRINTED, mode, "Should detect 100% printed pagination mode");

        // 3. Offset Resolution
        PageOffsetResolver resolver = new PageOffsetResolver(pages);
        assertEquals(Optional.of(1), resolver.toPageIndex(1));
        assertEquals(Optional.of(50), resolver.toPageIndex(50));

        // 4. Parse TOC entries from Page 54
        RawToc rawToc = parseTocFromText(TOC_PAGE_54);
        assertEquals(11, rawToc.entries().size(), "TOC should contain exactly 11 chapter entries");

        // 5. Monotonic Sequence Alignment
        List<TocAligner.AlignedSection> alignedSections = tocAligner.align(rawToc, pages, resolver, mode);
        assertEquals(11, alignedSections.size());

        // Check alignment accuracy for all chapters
        Map<String, Integer> expectedPageMap = Map.ofEntries(
                Map.entry("توطئة", 1),
                Map.entry("الكآبة الخرساء", 3),
                Map.entry("يد القضاء", 5),
                Map.entry("في باب الهيكل", 8),
                Map.entry("الشعلة البيضاء", 10),
                Map.entry("العاصفة", 12),
                Map.entry("بحيرة النار", 19),
                Map.entry("أمام عرش الموت", 33),
                Map.entry("بين عشتروت والمسيح", 41),
                Map.entry("التضحية", 44),
                Map.entry("المنقذ", 50)
        );

        for (TocAligner.AlignedSection s : alignedSections) {
            Integer expectedPage = expectedPageMap.get(s.title());
            assertNotNull(expectedPage, "Unmapped section: " + s.title());
            assertEquals(expectedPage.intValue(), s.startPage(),
                    "Section " + s.title() + " should align to page " + expectedPage);
            assertTrue(s.confidence().compareTo(BigDecimal.valueOf(0.80)) >= 0,
                    "Confidence for " + s.title() + " should be >= 0.80");
            assertFalse(s.needsReview(), "High confidence section should not need review: " + s.title());
        }

        // 6. Section Tree Construction
        Book book = new Book();
        book.setId(101L);
        book.setTitle("الأجنحة المتكسرة");

        SectionTreeBuilder.TreeBuildResult treeResult = sectionTreeBuilder.build(
                book,
                alignedSections,
                54,
                StructureSource.TEXT_LAYER
        );

        assertEquals(11, treeResult.sections().size());
        assertEquals(StructureStatus.RESOLVED, treeResult.status(), "Book structure should resolve cleanly");

        // Verify section boundaries
        List<BookSection> sections = treeResult.sections();
        assertEquals(1, sections.get(0).getStartPage());
        assertEquals(2, sections.get(0).getEndPage());

        assertEquals(3, sections.get(1).getStartPage());
        assertEquals(4, sections.get(1).getEndPage());

        assertEquals(5, sections.get(2).getStartPage());
        assertEquals(7, sections.get(2).getEndPage());

        assertEquals(8, sections.get(3).getStartPage());
        assertEquals(9, sections.get(3).getEndPage());

        assertEquals(10, sections.get(4).getStartPage());
        assertEquals(11, sections.get(4).getEndPage());

        assertEquals(12, sections.get(5).getStartPage());
        assertEquals(18, sections.get(5).getEndPage());

        assertEquals(19, sections.get(6).getStartPage());
        assertEquals(32, sections.get(6).getEndPage());

        assertEquals(33, sections.get(7).getStartPage());
        assertEquals(40, sections.get(7).getEndPage());

        assertEquals(41, sections.get(8).getStartPage());
        assertEquals(43, sections.get(8).getEndPage());

        assertEquals(44, sections.get(9).getStartPage());
        assertEquals(49, sections.get(9).getEndPage());

        assertEquals(50, sections.get(10).getStartPage());
        assertEquals(54, sections.get(10).getEndPage());

        long elapsedNanos = System.nanoTime() - startTime;
        double elapsedMillis = elapsedNanos / 1_000_000.0;
        System.out.printf("%n======================================================%n");
        System.out.printf(">>> [BENCHMARK] Full 54-Page Structure Resolution finished in %.2f ms%n", elapsedMillis);
        System.out.printf(">>> Total Sections Resolved: %d / 11 (100%% accuracy)%n", sections.size());
        System.out.printf(">>> Status: %s%n", treeResult.status());
        System.out.printf("======================================================%n%n");

        assertTrue(elapsedMillis < 100.0, "Resolution of 54 pages should execute well under 100ms");
    }

    @Test
    @DisplayName("Harmonization guard should protect intact text against corruption")
    void testHarmonizationGuardPerformance() {
        String original = "كنت في الثامنة عشرة من عمري عندما فتح الحب عينيّ بأشعته السحرية";
        String candidate = "كنت في الثامنة عشرة من عمري عندما فتح الحب عيني بأشعته السحرية"; // minor tashkeel diff

        assertTrue(harmonizationGuard.isValid(original, candidate),
                "Minor diacritic normalization should be accepted by guard");
    }

    private List<BookPage> build54Pages() {
        List<BookPage> list = new ArrayList<>();
        Map<Integer, String> headingsByPage = Map.ofEntries(
                Map.entry(1, "توطئة"),
                Map.entry(3, "الكآبة الخرساء"),
                Map.entry(5, "يد القضاء"),
                Map.entry(8, "في باب الهيكل"),
                Map.entry(10, "الشعلة البيضاء"),
                Map.entry(12, "العاصفة"),
                Map.entry(19, "بحيرة النار"),
                Map.entry(33, "أمام عرش الموت"),
                Map.entry(41, "بين عشتروت والمسيح"),
                Map.entry(44, "التضحية"),
                Map.entry(50, "المنقذ")
        );

        for (int i = 1; i <= 54; i++) {
            BookPage p = new BookPage();
            p.setPageNumber(i);
            p.setSourcePdfPage(i);
            p.setPrintedPageLabel(String.valueOf(i));

            String heading = headingsByPage.get(i);
            if (heading != null) {
                p.setMarkdownContent("# " + heading + "\n\nنص الصفحة رقم " + i);
                p.setHeadings("[{\"text\": \"" + heading + "\", \"levelHint\": 1}]");
            } else {
                p.setMarkdownContent("نص الصفحة العادية رقم " + i);
            }
            list.add(p);
        }
        return list;
    }

    private RawToc parseTocFromText(String tocText) {
        List<RawToc.RawTocEntry> entries = new ArrayList<>();
        String[] lines = tocText.split("\\r?\\n");
        for (String line : lines) {
            String[] parts = line.split("\\.{3,}");
            if (parts.length >= 2) {
                String pageNum = parts[0].trim();
                String title = parts[1].replaceAll("^[\\s\\p{Punct}]+|[\\s\\p{Punct}]+$", "").trim();
                if (!pageNum.isBlank() && !title.isBlank() && Character.isDigit(pageNum.charAt(0))) {
                    entries.add(new RawToc.RawTocEntry(
                            title,
                            null,
                            null,
                            1,
                            pageNum,
                            SectionType.CHAPTER
                    ));
                }
            }
        }
        return new RawToc(entries);
    }
}
