package com.doova.ktab.features.ocr.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ktab.ocr")
@Getter
@Setter
public class OcrProperties {

    private Image image = new Image();
    private Spread spread = new Spread();
    private Orientation orientation = new Orientation();
    private Quality quality = new Quality();
    private Repetition repetition = new Repetition();
    private RateLimit rateLimit = new RateLimit();
    private Toc toc = new Toc();
    private Structure structure = new Structure();
    private Harmonize harmonize = new Harmonize();

    private int parallelism = 8;
    private int chunkSize = 20;
    private int retryLimit = 3;
    private String promptVersion = "v2";
    private String systemPrompt;

    @Getter
    @Setter
    public static class Image {
        private int dpi = 300;
        private int retryDpi = 400;
        private int minLongSidePx = 2000;
        private int maxLongSidePx = 3500;
        private boolean preferEmbedded = true;
        private int jpegQuality = 90;
        private double maxBorderCropRatio = 0.15;
    }

    @Getter
    @Setter
    public static class Spread {
        private double aspectRatioThreshold = 1.2;
        private double gutterBandStart = 0.40;
        private double gutterBandEnd = 0.60;
    }

    @Getter
    @Setter
    public static class Orientation {
        private Precheck precheck = new Precheck();
        private int thumbnailLongSidePx = 768;

        @Getter
        @Setter
        public static class Precheck {
            private boolean enabled = true;
        }
    }

    @Getter
    @Setter
    public static class Quality {
        private double maxPoorRatio = 0.10;
    }

    @Getter
    @Setter
    public static class Repetition {
        private int maxRepeatedLines = 5;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int requestsPerMinute = 60;
    }

    @Getter
    @Setter
    public static class Toc {
        private int maxPages = 20;
    }

    @Getter
    @Setter
    public static class Structure {
        private int searchWindow = 2;
        private double matchThreshold = 0.80;
        private double reviewConfidence = 0.70;
        private double noPaginationConfidenceCap = 0.75;
        private double paginationPrintedMinRatio = 0.70;
        private double paginationNoneMaxRatio = 0.20;
    }

    @Getter
    @Setter
    public static class Harmonize {
        private boolean enabled = false;
        private String model = "";
        private double minSimilarity = 0.92;
    }
}
