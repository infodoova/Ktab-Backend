package com.doova.ktab.probe;

import com.doova.ktab.config.jpa.JpaAuditingConfig;
import com.doova.ktab.features.extraction.BookExtractionService;
import com.doova.ktab.features.extraction.config.TocLlmConfig;
import com.doova.ktab.features.extraction.config.TocLlmProperties;
import com.doova.ktab.features.extraction.config.ExtractionProperties;
import com.doova.ktab.features.extraction.dto.BookExtractionResult;
import com.doova.ktab.features.extraction.dto.TocEntry;
import com.doova.ktab.features.extraction.persist.ExtractionPersister;
import com.doova.ktab.features.extraction.pdf.PdfMetadataExtractor;
import com.doova.ktab.features.extraction.pdf.PdfPageExtractor;
import com.doova.ktab.features.extraction.pdf.PdfValidationService;
import com.doova.ktab.features.extraction.quality.StructureQualityChecker;
import com.doova.ktab.features.extraction.structure.*;
import com.doova.ktab.features.extraction.text.ArabicTextCleaner;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Commit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * THROWAWAY, never committed. Re-extracts one book's PDF with the native extraction (including the TOC classifier).
 * REEXTRACT_DRY=1 only reports; otherwise it replaces the book's saved pages and sections through ExtractionPersister.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, Book115Reextract.Beans.class, ExtractionProperties.class, TocLlmProperties.class, TocLlmConfig.class,
        TocLlmClassifier.class, PdfValidationService.class, PdfMetadataExtractor.class, PdfPageExtractor.class,
        ArabicTextCleaner.class, BookStructureExtractor.class, TocNormalizer.class, ChapterBuilder.class,
        StructureQualityChecker.class, ExtractionPersister.class, BookExtractionService.class, EmbeddedOutlineExtractor.class,
        HeadingDetector.class, PageNumberResolver.class, PrintedTocDetector.class, PrintedTocParser.class})
@EnabledIfEnvironmentVariable(named = "REEXTRACT_BOOK_ID", matches = "\\d+")
class Book115Reextract {

    @TestConfiguration
    static class Beans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        OpenAiApi openAiApi() {
            return OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
        }
    }

    @Autowired BookExtractionService extraction;
    @Autowired ExtractionPersister persister;
    @Autowired BookRepository books;

    @Test
    @Commit
    void reextract() throws Exception {
        long id = Long.parseLong(System.getenv("REEXTRACT_BOOK_ID"));
        boolean dry = "1".equals(System.getenv("REEXTRACT_DRY"));
        byte[] pdf = Files.readAllBytes(Path.of(System.getenv("REEXTRACT_PDF")));
        Book book = books.findById(id).orElseThrow();
        System.out.printf("REEXTRACT book=%d dry=%s pdfBytes=%d oldPageCount=%s%n", id, dry, pdf.length, book.getPageCount());

        BookExtractionResult result = extraction.extract(pdf);

        System.out.printf("REEXTRACT extracted pages=%d chapters=%d toc=%d source=%s confidence=%.2f warnings=%d%n",
                result.pages().size(), result.chapters().size(), result.toc().size(),
                result.structureDetection().source(), result.structureDetection().confidence(), result.warnings().size());
        result.warnings().forEach(w -> System.out.println("REEXTRACT warning " + w.code() + " " + w.severity() + " " + w.message()));
        print(result.toc(), 0);

        if (dry) {
            System.out.println("REEXTRACT dry run: nothing saved");
            return;
        }
        if (result.pages().isEmpty() || result.pages().size() < 0.9 * book.getPageCount() || result.toc().isEmpty()) {
            throw new IllegalStateException("extraction looks worse than what is saved; keeping the old text");
        }
        persister.persist(book, result);
        System.out.println("REEXTRACT saved");
    }

    private static void print(List<TocEntry> toc, int depth) {
        int[] shown = {0};
        walk(toc, depth, shown);
        System.out.println("REEXTRACT sections total=" + count(toc));
    }

    private static void walk(List<TocEntry> toc, int depth, int[] shown) {
        for (TocEntry e : toc) {
            if (shown[0]++ < 12) {
                System.out.printf("REEXTRACT   %s[L%d] p%d-%d %s%n", "  ".repeat(depth), e.level(), e.startPage(), e.endPage(), e.title());
            }
            walk(e.children(), depth + 1, shown);
        }
    }

    private static int count(List<TocEntry> toc) {
        int n = 0;
        for (TocEntry e : toc) {
            n += 1 + count(e.children());
        }
        return n;
    }
}
