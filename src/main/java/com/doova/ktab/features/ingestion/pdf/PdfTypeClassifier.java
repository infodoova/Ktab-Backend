package com.doova.ktab.features.ingestion.pdf;

import com.doova.ktab.enums.book.PdfType;
import com.doova.ktab.features.ingestion.config.IngestionProperties;
import com.doova.ktab.util.text.TextLayerQualityAssessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * Classifies a PDF as {@link PdfType#DIGITAL}, {@link PdfType#SCANNED}, {@link PdfType#HYBRID_OCR},
 * {@link PdfType#MIXED} or {@link PdfType#UNKNOWN}, before any rendering or OCR happens.
 * <p>
 * Sits above both ingestion pipelines (does not belong to {@code features.ocr} or
 * {@code features.studio}) and never defaults to {@code DIGITAL} on doubt: an unreadable,
 * encrypted, or unclassifiable PDF routes to OCR, the safe (if more expensive) path.
 * See docs/ocr_engine_v3.md, Phase 1.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PdfTypeClassifier {

    private final IngestionProperties properties;

    /**
     * @param pdfStream    the PDF to classify; caller owns the stream
     * @param languageCode e.g. {@code Book.getLanguage()}, used to pick the expected script
     *                     profile for the text-layer quality check; null defaults to Arabic
     */
    public PdfClassificationResult classify(InputStream pdfStream, String languageCode) {
        IngestionProperties.Classification cfg = properties.getClassification();
        long deadline = System.currentTimeMillis() + cfg.getTimeout().toMillis();

        try (RandomAccessRead rar = new RandomAccessReadBuffer(pdfStream);
             PDDocument doc = Loader.loadPDF(rar)) {

            if (doc.isEncrypted() && !doc.getCurrentAccessPermission().canExtractContent()) {
                // Legally and technically blocked from the text layer -> treat as scanned, not digital.
                return PdfClassificationResult.unknown(
                        "encrypted PDF; content extraction not permitted", 0);
            }

            int totalPages = doc.getNumberOfPages();
            if (totalPages == 0) {
                return PdfClassificationResult.unknown("zero-page document", 0);
            }

            List<Integer> sampledIndexes = selectSampleIndexes(totalPages, cfg);

            int digitalPages = 0;
            int scannedPages = 0;
            int hybridPages = 0;
            int blankPages = 0;
            int errorPages = 0;

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int pdfPageIndex : sampledIndexes) {
                if (System.currentTimeMillis() > deadline) {
                    return PdfClassificationResult.unknown("classification timed out", sampledIndexes.size());
                }

                try {
                    PDPage page = doc.getPage(pdfPageIndex);

                    stripper.setStartPage(pdfPageIndex + 1);
                    stripper.setEndPage(pdfPageIndex + 1);
                    String text = stripper.getText(doc);

                    TextLayerQualityAssessor.Assessment textAssessment =
                            TextLayerQualityAssessor.assess(text, languageCode, cfg.getArabicSanityRatio());

                    boolean hasEnoughText = text != null
                            && text.trim().length() > cfg.getMinCharsPerPage()
                            && textAssessment.passesSanityCheck();

                    boolean hasLargeImage = hasLargeImage(page, cfg);

                    if (hasEnoughText && hasLargeImage) {
                        // Usually a scanned page with an invisible/foreign OCR text layer.
                        hybridPages++;
                    } else if (!hasEnoughText && hasLargeImage) {
                        // Image-only scanned page.
                        scannedPages++;
                    } else if (hasEnoughText) {
                        // Normal digitally-generated page.
                        digitalPages++;
                    } else {
                        // Neither: blank verso, divider, or a plate page. Counted separately so
                        // it doesn't silently deflate every ratio's denominator (see Phase 1.3).
                        blankPages++;
                    }
                } catch (Exception e) {
                    errorPages++;
                    log.warn("PDF classification failed on page index {}: {}", pdfPageIndex, e.getMessage());
                }
            }

            int sampled = sampledIndexes.size();
            if (sampled == 0 || (double) errorPages / sampled > 0.20) {
                return PdfClassificationResult.unknown("too many unreadable pages", sampled);
            }

            int classifiable = digitalPages + scannedPages + hybridPages;
            if (classifiable == 0 || classifiable < 0.5 * sampled) {
                return PdfClassificationResult.builder()
                        .pdfType(PdfType.UNKNOWN)
                        .reason("insufficient classifiable pages")
                        .totalPages(totalPages)
                        .sampledPages(sampled)
                        .digitalPages(digitalPages)
                        .scannedPages(scannedPages)
                        .hybridPages(hybridPages)
                        .blankPages(blankPages)
                        .errorPages(errorPages)
                        .build();
            }

            double digitalRatio = (double) digitalPages / classifiable;
            double scannedRatio = (double) scannedPages / classifiable;
            double hybridRatio = (double) hybridPages / classifiable;

            PdfType type;
            if (digitalRatio >= cfg.getDigitalRatio()) {
                type = PdfType.DIGITAL;
            } else if (scannedRatio >= cfg.getScannedRatio()) {
                type = PdfType.SCANNED;
            } else if (hybridRatio >= cfg.getHybridRatio()) {
                type = PdfType.HYBRID_OCR;
            } else {
                type = PdfType.MIXED;
            }

            return PdfClassificationResult.builder()
                    .pdfType(type)
                    .totalPages(totalPages)
                    .sampledPages(sampled)
                    .digitalPages(digitalPages)
                    .scannedPages(scannedPages)
                    .hybridPages(hybridPages)
                    .blankPages(blankPages)
                    .errorPages(errorPages)
                    .digitalRatio(digitalRatio)
                    .scannedRatio(scannedRatio)
                    .hybridRatio(hybridRatio)
                    .build();

        } catch (Exception e) {
            log.warn("PDF classification failed entirely: {}", e.getMessage(), e);
            return PdfClassificationResult.unknown("exception: " + e.getMessage(), 0);
        }
    }

    private boolean hasLargeImage(PDPage page, IngestionProperties.Classification cfg) throws Exception {
        double pageArea = cropBoxArea(page);
        ImageCoverageEngine engine = new ImageCoverageEngine(pageArea, cfg.getFormXObjectMaxDepth());
        engine.processPage(page);

        long minPixels = (long) cfg.getMinImagePixelsWidth() * cfg.getMinImagePixelsHeight();
        return engine.maxCoverage() >= cfg.getImageCoverageThreshold()
                && engine.largestContributingPixels() >= minPixels;
    }

    private double cropBoxArea(PDPage page) {
        PDRectangle box = page.getCropBox();
        return (double) box.getWidth() * box.getHeight();
    }

    /**
     * Deterministic sampling: full scan for small books, otherwise the first 10 pages
     * (front matter, cheap and worth seeing), the last 3, and an even spread in between
     * up to {@code maxSampledPages}. See docs/ocr_engine_v3.md, Phase 1.5.
     */
    private List<Integer> selectSampleIndexes(int totalPages, IngestionProperties.Classification cfg) {
        int maxSampled = cfg.getMaxSampledPages();
        if (maxSampled <= 0 || totalPages <= cfg.getFullScanUnderPages() || totalPages <= maxSampled) {
            return IntStream.range(0, totalPages).boxed().toList();
        }

        SortedSet<Integer> indexes = new TreeSet<>();

        int firstN = Math.min(10, totalPages);
        for (int i = 0; i < firstN; i++) {
            indexes.add(i);
        }

        int lastN = Math.min(3, totalPages);
        for (int i = totalPages - lastN; i < totalPages; i++) {
            indexes.add(i);
        }

        int remaining = maxSampled - indexes.size();
        if (remaining > 0) {
            double step = (double) totalPages / remaining;
            for (int i = 0; i < remaining; i++) {
                indexes.add((int) Math.min(totalPages - 1, Math.round(i * step)));
            }
        }

        return new ArrayList<>(indexes);
    }
}
