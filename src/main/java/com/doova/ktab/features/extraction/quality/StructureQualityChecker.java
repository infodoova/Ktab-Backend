package com.doova.ktab.features.extraction.quality;

import com.doova.ktab.features.extraction.config.ExtractionProperties;
import com.doova.ktab.features.extraction.dto.Chapter;
import com.doova.ktab.features.extraction.dto.ExtractionWarning;
import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.dto.Severity;
import com.doova.ktab.features.extraction.dto.WarningCode;
import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;
import com.doova.ktab.features.ocr.text.SectionClassifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Phase 5: return warnings instead of silently accepting suspicious structure. */
@Component
public class StructureQualityChecker {

    private static final int GAP_MIN_CHAPTERS = 3;
    private static final double ARTIFACT_RATIO = 0.01;
    private static final double IMAGE_ONLY_ERROR_RATIO = 0.20;

    private final ExtractionProperties props;

    public StructureQualityChecker(ExtractionProperties props) {
        this.props = props;
    }

    public List<ExtractionWarning> check(List<Chapter> chapters, int pageCount, double arabicRatio) {
        List<ExtractionWarning> w = new ArrayList<>();
        if (arabicRatio < props.getMinArabicRatio()) {
            w.add(warn(WarningCode.LOW_ARABIC_RATIO, Severity.ERROR,
                    String.format("Only %.0f%% of the extracted letters are Arabic; the text layer may be garbled.", arabicRatio * 100)));
        }
        Set<String> titles = new HashSet<>();
        List<Integer> ordinals = new ArrayList<>();
        for (Chapter c : chapters) {
            if (c.startPage() > c.endPage() || c.startPage() < 1 || c.endPage() > pageCount) {
                w.add(warn(WarningCode.INVALID_RANGE, Severity.ERROR,
                        "Chapter \"" + c.title() + "\" has an invalid page range " + c.startPage() + "-" + c.endPage() + "."));
            }
            if (!titles.add(ArabicTextNormalizer.normalize(c.title()))) {
                w.add(warn(WarningCode.DUPLICATE_HEADING, Severity.WARNING, "The heading \"" + c.title() + "\" appears more than once."));
            }
            if (c.text() == null || c.text().isBlank()) {
                w.add(warn(WarningCode.EMPTY_CHAPTER, Severity.WARNING, "Chapter \"" + c.title() + "\" has no text."));
            } else if (c.text().length() < props.getShortChapterChars()) {
                w.add(warn(WarningCode.SHORT_CHAPTER, Severity.WARNING,
                        "Chapter \"" + c.title() + "\" is unexpectedly short (" + c.text().length() + " characters)."));
            }
            if (chapters.size() >= GAP_MIN_CHAPTERS && pageCount > 0
                    && (double) (c.endPage() - c.startPage() + 1) / pageCount > props.getLargeGapRatio()) {
                w.add(warn(WarningCode.LARGE_GAP, Severity.WARNING,
                        "Chapter \"" + c.title() + "\" covers most of the book; chapters may have been missed."));
            }
            if (ArabicTextNormalizer.normalize(c.title()).startsWith("الفصل")) {
                Optional<Integer> ordinal = SectionClassifier.extractOrdinal(c.title());
                ordinal.ifPresent(ordinals::add);
            }
        }
        for (int i = 1; i < ordinals.size(); i++) {
            if (ordinals.get(i) > ordinals.get(i - 1) + 1) {
                w.add(warn(WarningCode.MISSING_CHAPTERS, Severity.WARNING,
                        "Chapter numbering skips from " + ordinals.get(i - 1) + " to " + ordinals.get(i) + "."));
                break;
            }
        }
        return w;
    }

    public List<ExtractionWarning> checkPages(List<PageContent> pages) {
        List<ExtractionWarning> w = new ArrayList<>();
        List<Integer> imageOnly = pages.stream().filter(PageContent::imageOnly).map(PageContent::pdfPage).toList();
        if (!imageOnly.isEmpty()) {
            boolean mostly = !pages.isEmpty() && (double) imageOnly.size() / pages.size() > IMAGE_ONLY_ERROR_RATIO;
            w.add(warn(WarningCode.IMAGE_ONLY_PAGES, mostly ? Severity.ERROR : Severity.WARNING,
                    "These pages have no text layer (scanned or image-only) and are empty: "
                            + imageOnly.stream().map(String::valueOf).collect(Collectors.joining(", ")) + "."));
        }
        long total = 0;
        long bad = 0;
        for (PageContent p : pages) {
            String t = p.cleanedText();
            total += t.length();
            bad += t.chars().filter(c -> c == '\uFFFD').count();
        }
        if (total > 0 && (double) bad / total > ARTIFACT_RATIO) {
            w.add(warn(WarningCode.EXTRACTION_ARTIFACTS, Severity.WARNING, "The text contains many unreadable replacement characters."));
        }
        return w;
    }

    private static ExtractionWarning warn(WarningCode code, Severity severity, String message) {
        return new ExtractionWarning(code, severity, message);
    }
}
