package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.DetectionSource;
import com.doova.ktab.features.extraction.dto.ExtractionWarning;
import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.dto.Severity;
import com.doova.ktab.features.extraction.dto.WarningCode;
import com.doova.ktab.features.ocr.text.SectionClassifier;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Spec step 5: embedded outline, then printed TOC, then headings; reports which one won and how sure it is. */
@Component
public class BookStructureExtractor {

    public record StructureResult(DetectionSource source, double confidence, List<RawEntry> entries,
                                  List<ExtractionWarning> warnings) {
    }

    private final EmbeddedOutlineExtractor outline;
    private final PrintedTocDetector tocDetector;
    private final PrintedTocParser tocParser;
    private final PageNumberResolver resolver;
    private final HeadingDetector headings;
    private final TocLlmClassifier llmClassifier;
    private final com.doova.ktab.features.extraction.pdf.PdfPageExtractor pdfPageExtractor;

    public BookStructureExtractor(EmbeddedOutlineExtractor outline, PrintedTocDetector tocDetector,
                                  PrintedTocParser tocParser, PageNumberResolver resolver,
                                  HeadingDetector headings) {
        this(outline, tocDetector, tocParser, resolver, headings, null, null);
    }

    public BookStructureExtractor(EmbeddedOutlineExtractor outline, PrintedTocDetector tocDetector,
                                  PrintedTocParser tocParser, PageNumberResolver resolver,
                                  HeadingDetector headings, TocLlmClassifier llmClassifier) {
        this(outline, tocDetector, tocParser, resolver, headings, llmClassifier, null);
    }

    @Autowired
    public BookStructureExtractor(EmbeddedOutlineExtractor outline, PrintedTocDetector tocDetector,
                                  PrintedTocParser tocParser, PageNumberResolver resolver,
                                  HeadingDetector headings,
                                  @Autowired(required = false) TocLlmClassifier llmClassifier,
                                  @Autowired(required = false) com.doova.ktab.features.extraction.pdf.PdfPageExtractor pdfPageExtractor) {
        this.outline = outline;
        this.tocDetector = tocDetector;
        this.tocParser = tocParser;
        this.resolver = resolver;
        this.headings = headings;
        this.llmClassifier = llmClassifier;
        this.pdfPageExtractor = pdfPageExtractor;
    }

    /**
     * Full extraction with language and title context forwarded to the LLM classifier.
     *
     * @param doc       the loaded PDF document
     * @param pages     all extracted page contents
     * @param language  book language code ("ar" / "en"), may be null
     * @param bookTitle book title for LLM context, may be null
     */
    public StructureResult extract(PDDocument doc, List<PageContent> pages,
                                   String language, String bookTitle) {
        List<ExtractionWarning> warnings = new ArrayList<>();

        // --- Try 1: embedded PDF outline (already hierarchical, skip LLM) ---
        List<RawEntry> fromOutline = outline.extract(doc);
        if (fromOutline.size() >= 2) {
            return new StructureResult(DetectionSource.EMBEDDED_OUTLINE, 1.0, fromOutline, warnings);
        }
        warnings.add(info("The PDF has no usable embedded outline."));

        // --- Try 2: printed TOC pages ---
        List<Integer> tocPageNumbers = tocDetector.findTocPages(pages);
        if (!tocPageNumbers.isEmpty()) {
            List<PageContent> tocContent = pages.stream()
                    .filter(p -> tocPageNumbers.contains(p.pdfPage()))
                    .toList();

            // Collect raw text for the LLM (using formatted text with bold hints if available)
            String rawTocText;
            if (pdfPageExtractor != null && doc != null) {
                try {
                    StringBuilder sb = new StringBuilder();
                    for (int pageNum : tocPageNumbers) {
                        List<String> formatted = pdfPageExtractor.extractFormattedLines(doc, pageNum);
                        sb.append(String.join("\n", formatted)).append("\n");
                    }
                    rawTocText = sb.toString().strip();
                } catch (Exception ex) {
                    rawTocText = tocContent.stream()
                            .map(PageContent::rawText)
                            .collect(Collectors.joining("\n"));
                }
            } else {
                rawTocText = tocContent.stream()
                        .map(PageContent::rawText)
                        .collect(Collectors.joining("\n"));
            }

            List<RawEntry> heuristicEntries = tocParser.parse(tocContent);

            // Always try the LLM when a TOC is detected — it handles formats the parser drops
            List<RawEntry> llmEntries = (llmClassifier != null)
                    ? llmClassifier.classify(rawTocText, language, bookTitle, heuristicEntries)
                    : List.of();

            // Use LLM entries if we got something useful, otherwise fall back to heuristic
            List<RawEntry> tocEntries = llmEntries.size() >= 2 ? llmEntries : heuristicEntries;

            if (tocEntries.size() >= 2) {
                PageNumberResolver.Resolution r = resolver.resolve(tocEntries, pages);
                if (r.entries().size() >= 2 && r.confidence() >= 0.4) {
                    warnings.addAll(r.warnings());
                    return new StructureResult(DetectionSource.PRINTED_TOC, r.confidence(), r.entries(), warnings);
                }
                warnings.add(info("A printed TOC was found but its pages could not be mapped reliably."));
            }
        } else {
            warnings.add(info("No printed table of contents was found."));
        }

        // --- Try 3: heading detection (heuristic, no LLM) ---
        List<RawEntry> detected = headings.detect(pages);
        if (detected.size() >= 2) {
            Set<Integer> ordinals = new HashSet<>();
            for (RawEntry e : detected) {
                if (e.level() == 1) {
                    SectionClassifier.extractOrdinal(e.title()).ifPresent(ordinals::add);
                }
            }
            return new StructureResult(DetectionSource.HEADING_DETECTION,
                    ordinals.size() >= 2 ? 0.7 : 0.55, detected, warnings);
        }
        warnings.add(new ExtractionWarning(WarningCode.NO_STRUCTURE, Severity.ERROR,
                "No outline, printed table of contents or chapter headings could be detected."));
        return new StructureResult(DetectionSource.NONE, 0.0, List.of(), warnings);
    }

    /**
     * Backward-compatible overload — no language/title context (LLM will still run but without context).
     */
    public StructureResult extract(PDDocument doc, List<PageContent> pages) {
        return extract(doc, pages, null, null);
    }

    private static ExtractionWarning info(String message) {
        return new ExtractionWarning(WarningCode.NO_STRUCTURE, Severity.INFO, message);
    }
}
