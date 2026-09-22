package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.ReadingDirection;
import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureStatus;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.ocr.ai.GeminiOcrService;
import com.doova.ktab.features.ocr.dto.GeminiOcrResponse;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
class ProductionOcrEndToEndJobTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("ocrJob")
    private Job ocrJob;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookPageRepository pageRepository;

    @Autowired
    private BookSectionRepository sectionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private S3OcrStorageService s3;

    @MockBean
    private GeminiOcrService geminiOcrService;

    @Test
    @DisplayName("Simulate Production Book Upload & Full Spring Batch ocrJob (54 Pages)")
    void testProductionOcrJobExecutionWithRealTimings() throws Exception {
        System.out.println("================================================================================");
        System.out.println(">>> SIMULATING PRODUCTION BOOK UPLOAD: الأجنحة المتكسرة (54 Pages)");
        System.out.println("================================================================================");

        long pipelineStart = System.currentTimeMillis();

        // 1. Sync PostgreSQL sequences
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('tbl_book_pages', 'col_id'), (SELECT COALESCE(MAX(col_id), 1) FROM tbl_book_pages));");
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('tbl_books', 'col_id'), (SELECT COALESCE(MAX(col_id), 1) FROM tbl_books));");
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('tbl_book_sections', 'col_id'), (SELECT COALESCE(MAX(col_id), 1) FROM tbl_book_sections));");

        // 2. Persist Book as created upon upload
        Book book = new Book();
        book.setTitle("الأجنحة المتكسرة");
        book.setReadingDirection(ReadingDirection.RTL);
        book.setOcrStatus(OcrStatus.PENDING);
        book = bookRepository.save(book);
        Long bookId = book.getId();

        // 3. Load the user's real uploaded 54-page PDF document
        byte[] pdfBytes = loadUploadedPdf();
        String pdfKey = "books/" + bookId + "/source.pdf";

        // 4. Mock S3 storage for the PDF and rendered PNGs
        Map<Integer, byte[]> pngStore = new ConcurrentHashMap<>();
        Mockito.when(s3.getStream(anyString())).thenAnswer(inv -> new ByteArrayInputStream(pdfBytes));
        Mockito.doAnswer(inv -> {
            int page = inv.getArgument(1);
            byte[] bytes = inv.getArgument(2);
            pngStore.put(page, bytes);
            return null;
        }).when(s3).uploadPagePng(eq(bookId), anyInt(), any(byte[].class));

        Mockito.when(s3.generatePresignedUrl(anyString())).thenAnswer(inv ->
                "https://storage.ktab.ai/" + inv.getArgument(0));

        // 5. Setup OCR responses with chapter headings for structure resolution
        Map<Integer, String> headingsMap = Map.ofEntries(
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

        // Pre-extract text from PDF if available, or use text template
        Map<Integer, String> pageTexts = extractPageTextsFromPdf(pdfBytes);

        Mockito.when(geminiOcrService.ocrOnePageFromUrl(anyString(), anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            int pageNum = extractPageNumberFromUrl(url);
            String heading = headingsMap.get(pageNum);

            List<com.doova.ktab.features.ocr.batch.OcrResult.DetectedHeading> headings = heading != null
                    ? List.of(new com.doova.ktab.features.ocr.batch.OcrResult.DetectedHeading(heading, 1))
                    : List.of();

            PageKind kind = (pageNum == 54) ? PageKind.TOC : PageKind.BODY;
            String text = pageTexts.getOrDefault(pageNum, "نص كتاب الأجنحة المتكسرة للصفحة رقم " + pageNum);
            if (pageNum == 54) {
                text = text + "\n" + TOC_CONTENT_PAGE_54;
            }

            int wordCount = Math.max(1, text.split("\\s+").length);

            return new GeminiOcrResponse(
                    kind,
                    0,
                    com.doova.ktab.enums.book.ImageQuality.GOOD,
                    false,
                    false,
                    0,
                    String.valueOf(pageNum),
                    "جبران خليل جبران - الأجنحة المتكسرة",
                    headings,
                    text,
                    "",
                    false,
                    false,
                    wordCount,
                    "STOP"
            );
        });

        // 6. Launch full Spring Batch ocrJob (Real Production Pipeline)
        JobParameters params = new JobParametersBuilder()
                .addLong("bookId", bookId)
                .addString("pdfKey", pdfKey)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncher.run(ocrJob, params);
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(), "OCR Job must complete successfully");

        long pipelineEnd = System.currentTimeMillis();
        long totalExecutionMs = pipelineEnd - pipelineStart;

        // 7. Verify and display Step-by-Step execution times from Spring Batch
        System.out.println("\n================================================================================");
        System.out.println(">>> REAL PRODUCTION SPRING BATCH PIPELINE BREAKDOWN (ocrJob):");
        System.out.println("================================================================================");

        for (StepExecution step : jobExecution.getStepExecutions()) {
            Duration duration = Duration.between(step.getStartTime(), step.getEndTime());
            System.out.printf("  Step: %-26s | Status: %-9s | Read: %3d | Write: %3d | Duration: %5d ms%n",
                    step.getStepName(),
                    step.getStatus(),
                    step.getReadCount(),
                    step.getWriteCount(),
                    duration.toMillis());
        }

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("  TOTAL PRODUCTION PIPELINE TIME: %d ms (%.2f seconds)%n",
                totalExecutionMs, totalExecutionMs / 1000.0);
        System.out.println("================================================================================\n");

        // 8. VERIFY POSTGRESQL DATABASE PERSISTENCE
        Book dbBook = bookRepository.findById(bookId).orElseThrow();
        assertEquals(OcrStatus.COMPLETED, dbBook.getOcrStatus(), "Book OCR status must be COMPLETED in DB");
        assertEquals(StructureStatus.RESOLVED, dbBook.getStructureStatus(), "Structure status must be RESOLVED in DB");

        List<BookPage> dbPages = pageRepository.findByBookIdOrderByPageNumberAsc(bookId);
        assertFalse(dbPages.isEmpty(), "Pages must be persisted in tbl_book_pages");
        System.out.printf(">>> Total Pages Persisted to DB: %d (Rendered PNGs in S3: %d)%n",
                dbPages.size(), pngStore.size());

        List<BookSection> dbSections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);
        assertFalse(dbSections.isEmpty(), "Chapters must be persisted in tbl_book_sections");
        System.out.printf(">>> Total Sections Persisted to DB: %d%n", dbSections.size());
        for (BookSection s : dbSections) {
            System.out.printf("    - Section: %-25s | Level: %d | PageRange: %d-%d%n",
                    s.getTitle(), s.getLevel(), s.getStartPage(), s.getEndPage());
        }

        // Verify foreign key linkages
        int pagesLinked = 0;
        for (BookPage p : dbPages) {
            if (p.getSection() != null) {
                pagesLinked++;
            }
        }
        System.out.printf(">>> Total Pages Linked to Sections in DB: %d / %d%n", pagesLinked, dbPages.size());
        assertEquals(dbPages.size(), pagesLinked, "Every page must have col_section_id linked to its section in DB");

        System.out.printf(">>> SUCCESS: Production OCR Pipeline executed and 100%% verified in PostgreSQL!%n%n");
    }

    private byte[] loadUploadedPdf() throws Exception {
        java.io.File file = new java.io.File("C:/Users/PC/.gemini/antigravity-ide/brain/b1952b0a-76c8-4070-8403-37ac063f64f6/.user_uploaded/media_1789967820810.pdf");
        if (!file.exists()) {
            throw new IllegalStateException("Uploaded PDF file not found at " + file.getAbsolutePath());
        }
        return java.nio.file.Files.readAllBytes(file.toPath());
    }

    private Map<Integer, String> extractPageTextsFromPdf(byte[] pdfBytes) {
        Map<Integer, String> map = new HashMap<>();
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc).trim();
                map.put(i, text);
            }
        } catch (Exception e) {
            System.err.println("Could not extract text layer: " + e.getMessage());
        }
        return map;
    }

    private int extractPageNumberFromUrl(String url) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("page-(\\d+)").matcher(url);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            return 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private static final String TOC_CONTENT_PAGE_54 = """
            الفهرس
            جبران خليل جبران
            الأجنحة المتكسرة ـ 1 ـ توطئة
            **************
            1
            1 ...................................................................................................................................... توطئة
            3 ......................................................................................................................... الكآبة الخرساء -
            5 ............................................................................................................................... يد القضاء -
            8 ......................................................................................................................... في باب الهيكل -
            10 ........................................................................................................................ الشعلة البيضاء -
            12 ................................................................................................................................ العاصفة -
            19 ............................................................................................................................ بحيرة النار -
            33 ................................................................................................................... أمام عرش الموت -
            41 ............................................................................................................. بين عشتروت والمسيح -
            44 ............................................................................................................................... التضحية -
            50 ................................................................................................................................... المنقذ -
            54
            """;
}
