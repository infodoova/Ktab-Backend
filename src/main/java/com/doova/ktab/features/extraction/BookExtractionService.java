package com.doova.ktab.features.extraction;

import com.doova.ktab.features.extraction.dto.BookExtractionResult;
import com.doova.ktab.features.extraction.dto.BookMetadata;
import com.doova.ktab.features.extraction.dto.Chapter;
import com.doova.ktab.features.extraction.dto.ExtractionWarning;
import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.dto.StructureDetection;
import com.doova.ktab.features.extraction.dto.TocEntry;
import com.doova.ktab.features.extraction.pdf.PdfMetadataExtractor;
import com.doova.ktab.features.extraction.pdf.PdfPageExtractor;
import com.doova.ktab.features.extraction.pdf.PdfValidationService;
import com.doova.ktab.features.extraction.quality.StructureQualityChecker;
import com.doova.ktab.features.extraction.structure.BookStructureExtractor;
import com.doova.ktab.features.extraction.structure.ChapterBuilder;
import com.doova.ktab.features.extraction.structure.TocNormalizer;
import com.doova.ktab.features.extraction.text.ArabicTextCleaner;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** The whole spec in one call: a digital Arabic PDF in, metadata + pages + TOC + chapters + warnings out. No database. */
@Service
public class BookExtractionService {

    private final PdfValidationService validation;
    private final PdfMetadataExtractor metadataExtractor;
    private final PdfPageExtractor pageExtractor;
    private final ArabicTextCleaner cleaner;
    private final BookStructureExtractor structureExtractor;
    private final TocNormalizer normalizer;
    private final ChapterBuilder chapterBuilder;
    private final StructureQualityChecker quality;

    public BookExtractionService(PdfValidationService validation, PdfMetadataExtractor metadataExtractor,
                                 PdfPageExtractor pageExtractor, ArabicTextCleaner cleaner,
                                 BookStructureExtractor structureExtractor, TocNormalizer normalizer,
                                 ChapterBuilder chapterBuilder, StructureQualityChecker quality) {
        this.validation = validation;
        this.metadataExtractor = metadataExtractor;
        this.pageExtractor = pageExtractor;
        this.cleaner = cleaner;
        this.structureExtractor = structureExtractor;
        this.normalizer = normalizer;
        this.chapterBuilder = chapterBuilder;
        this.quality = quality;
    }

    public BookExtractionResult extract(byte[] pdf) {
        try (PDDocument doc = validation.load(pdf)) {
            BookMetadata metadata = metadataExtractor.extract(doc);
            List<PageContent> pages = cleaner.clean(pageExtractor.extract(doc), metadata);
            BookStructureExtractor.StructureResult structure = structureExtractor.extract(
                    doc, pages, metadata.language(), metadata.title());
            List<TocEntry> toc = normalizer.normalize(structure.entries(), pages.size());
            List<Chapter> chapters = chapterBuilder.build(toc, pages);

            List<ExtractionWarning> warnings = new ArrayList<>(structure.warnings());
            double arRatio = arabicRatio(pages);
            boolean isEnglish = (metadata.language() != null && metadata.language().toLowerCase().startsWith("en"))
                    || isPredominantlyLatin(pages);
            warnings.addAll(quality.check(chapters, pages.size(), isEnglish ? 1.0 : arRatio));
            warnings.addAll(quality.checkPages(pages));
            return new BookExtractionResult(metadata, new StructureDetection(structure.source(), structure.confidence()),
                    toc, chapters, pages, warnings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Arabic letters as a share of all letters in the cleaned text. */
    static double arabicRatio(List<PageContent> pages) {
        long arabic = 0;
        long letters = 0;
        for (PageContent p : pages) {
            for (int i = 0; i < p.cleanedText().length(); i++) {
                char c = p.cleanedText().charAt(i);
                if (Character.isLetter(c)) {
                    letters++;
                    if (c >= '\u0600' && c <= '\u06FF' || c >= '\u0750' && c <= '\u077F' || c >= '\uFB50' && c <= '\uFEFF') {
                        arabic++;
                    }
                }
            }
        }
        return letters == 0 ? 0 : (double) arabic / letters;
    }

    static boolean isPredominantlyLatin(List<PageContent> pages) {
        long latin = 0;
        long arabic = 0;
        for (PageContent p : pages) {
            for (int i = 0; i < p.cleanedText().length(); i++) {
                char c = p.cleanedText().charAt(i);
                if (Character.isLetter(c)) {
                    if (c < 0x0250) {
                        latin++;
                    } else if (c >= '\u0600' && c <= '\u06FF' || c >= '\u0750' && c <= '\u077F' || c >= '\uFB50' && c <= '\uFEFF') {
                        arabic++;
                    }
                }
            }
        }
        return latin > arabic;
    }
}
