package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.DetectionSource;
import com.doova.ktab.features.extraction.dto.ExtractionWarning;
import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.dto.Severity;
import com.doova.ktab.features.extraction.dto.WarningCode;
import com.doova.ktab.features.ocr.text.SectionClassifier;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public BookStructureExtractor(EmbeddedOutlineExtractor outline, PrintedTocDetector tocDetector, PrintedTocParser tocParser,
                                  PageNumberResolver resolver, HeadingDetector headings) {
        this.outline = outline;
        this.tocDetector = tocDetector;
        this.tocParser = tocParser;
        this.resolver = resolver;
        this.headings = headings;
    }

    public StructureResult extract(PDDocument doc, List<PageContent> pages) {
        List<ExtractionWarning> warnings = new ArrayList<>();

        List<RawEntry> fromOutline = outline.extract(doc);
        if (fromOutline.size() >= 2) {
            return new StructureResult(DetectionSource.EMBEDDED_OUTLINE, 1.0, fromOutline, warnings);
        }
        warnings.add(info("The PDF has no usable embedded outline."));

        List<Integer> tocPages = tocDetector.findTocPages(pages);
        if (!tocPages.isEmpty()) {
            List<PageContent> tocContent = pages.stream().filter(p -> tocPages.contains(p.pdfPage())).toList();
            List<RawEntry> printed = tocParser.parse(tocContent);
            if (printed.size() >= 2) {
                PageNumberResolver.Resolution r = resolver.resolve(printed, pages);
                if (r.entries().size() >= 2 && r.confidence() >= 0.5) {
                    warnings.addAll(r.warnings());
                    return new StructureResult(DetectionSource.PRINTED_TOC, r.confidence(), r.entries(), warnings);
                }
                warnings.add(info("A printed TOC was found but its pages could not be mapped reliably."));
            }
        } else {
            warnings.add(info("No printed table of contents was found."));
        }

        List<RawEntry> detected = headings.detect(pages);
        if (detected.size() >= 2) {
            Set<Integer> ordinals = new HashSet<>();
            for (RawEntry e : detected) {
                if (e.level() == 1) {
                    SectionClassifier.extractOrdinal(e.title()).ifPresent(ordinals::add);
                }
            }
            return new StructureResult(DetectionSource.HEADING_DETECTION, ordinals.size() >= 2 ? 0.7 : 0.55, detected, warnings);
        }
        warnings.add(new ExtractionWarning(WarningCode.NO_STRUCTURE, Severity.ERROR,
                "No outline, printed table of contents or chapter headings could be detected."));
        return new StructureResult(DetectionSource.NONE, 0.0, List.of(), warnings);
    }

    private static ExtractionWarning info(String message) {
        return new ExtractionWarning(WarningCode.NO_STRUCTURE, Severity.INFO, message);
    }
}
