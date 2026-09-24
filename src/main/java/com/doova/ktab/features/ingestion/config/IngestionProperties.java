package com.doova.ktab.features.ingestion.config;

import com.doova.ktab.enums.book.IngestionRoute;
import com.doova.ktab.enums.book.PdfType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration for the PDF classifier that runs before either ingestion
 * pipeline
 * (studioIngestionJob / ocrJob). Deliberately not part of {@code OcrProperties}
 * —
 * the classifier sits above both pipelines, not inside either one.
 * See docs/ocr_engine_v3.md, Phase 0 / Phase 1.
 */
@Configuration
@ConfigurationProperties(prefix = "ktab.ingestion")
@Getter
@Setter
public class IngestionProperties {

    private Classification classification = new Classification();

    @Getter
    @Setter
    public static class Classification {

        /** Master switch. When false, every book routes to OCR unconditionally. */
        private boolean enabled = true;

        /**
         * When true, classify and persist the result but always route to OCR anyway.
         * Used for the rollout period (docs/ocr_engine_v3.md, Phase 5) to compare
         * classifier output against real outcomes before it drives any routing.
         */
        private boolean shadowMode = false;

        /** A page counts as "has text" once its extracted text exceeds this length. */
        private int minCharsPerPage = 50;

        /**
         * Cap on how many pages are sampled for classification. 0 = scan every page.
         */
        private int maxSampledPages = 40;

        /**
         * Below this page count, always scan every page regardless of maxSampledPages.
         */
        private int fullScanUnderPages = 60;

        /**
         * Minimum fraction of the crop-box area a placed image must cover to count as
         * "large".
         */
        private double imageCoverageThreshold = 0.80;

        private int minImagePixelsWidth = 700;
        private int minImagePixelsHeight = 900;

        /**
         * Fraction of classifiable pages that must be DIGITAL for the book to classify
         * as DIGITAL.
         */
        private double digitalRatio = 0.70;

        /**
         * Fraction of classifiable pages that must be SCANNED for the book to classify
         * as SCANNED.
         */
        private double scannedRatio = 0.70;

        /**
         * Fraction of classifiable pages that must be HYBRID_OCR for the book to
         * classify as HYBRID_OCR.
         */
        private double hybridRatio = 0.70;

        /**
         * Minimum ratio of Arabic letters to total letters for a text layer to be
         * trusted.
         */
        private double arabicSanityRatio = 0.60;

        /**
         * Recursion depth limit when walking nested Form XObjects for image detection.
         */
        private int formXObjectMaxDepth = 8;

        /**
         * Hard wall-clock budget for classifying a single book; on expiry the result is
         * UNKNOWN.
         */
        private Duration timeout = Duration.ofSeconds(60);

        /**
         * Resolved PdfType -> IngestionRoute mapping. UNKNOWN always routes to OCR
         * regardless
         * of what is configured here (see docs/ocr_engine_v3.md, "Authority by route").
         */
        private Map<PdfType, IngestionRoute> routing = defaultRouting();

        private static Map<PdfType, IngestionRoute> defaultRouting() {
            Map<PdfType, IngestionRoute> routing = new EnumMap<>(PdfType.class);
            routing.put(PdfType.DIGITAL, IngestionRoute.STUDIO);
            routing.put(PdfType.SCANNED, IngestionRoute.OCR);
            routing.put(PdfType.HYBRID_OCR, IngestionRoute.OCR);
            routing.put(PdfType.MIXED, IngestionRoute.OCR);
            routing.put(PdfType.UNKNOWN, IngestionRoute.OCR);
            return routing;
        }
    }
}
