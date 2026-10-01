package com.doova.ktab.features.extraction;

import com.doova.ktab.features.extraction.dto.BookMetadata;
import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.pdf.PdfMetadataExtractor;
import com.doova.ktab.features.extraction.pdf.PdfPageExtractor;
import com.doova.ktab.features.extraction.pdf.PdfValidationService;
import com.doova.ktab.features.extraction.text.ArabicTextCleaner;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Loads the committed PDFs in src/test/resources/extraction/ (regenerate with ExtractionFixtureGenerator). */
public final class ExtractionTestSupport {

    private static final Path DIR = Path.of("src/test/resources/extraction");

    private ExtractionTestSupport() {
    }

    public static byte[] fixture(String name) {
        try {
            return Files.readAllBytes(DIR.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PDDocument load(String name) {
        return new PdfValidationService(200L * 1024 * 1024).load(fixture(name));
    }

    public static List<PageContent> extract(String name) throws IOException {
        try (PDDocument doc = load(name)) {
            return new PdfPageExtractor().extract(doc);
        }
    }

    public static List<PageContent> cleanedPages(String name) throws IOException {
        try (PDDocument doc = load(name)) {
            BookMetadata meta = new PdfMetadataExtractor().extract(doc);
            return new ArabicTextCleaner().clean(new PdfPageExtractor().extract(doc), meta);
        }
    }
}
