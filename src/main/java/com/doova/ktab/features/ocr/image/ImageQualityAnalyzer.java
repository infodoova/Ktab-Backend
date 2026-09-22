package com.doova.ktab.features.ocr.image;

import com.doova.ktab.enums.book.ImageQuality;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class ImageQualityAnalyzer {

    public record ImageQualityAnalysis(ImageQuality quality, Map<String, Object> metrics) {}

    /**
     * Computes deterministic image metrics (contrast, ink density, bleed-through)
     * and derives an initial ImageQuality tier.
     */
    public ImageQualityAnalysis analyze(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int step = Math.max(1, (int) Math.sqrt((double) (width * height) / 10000.0));

        long sumLum = 0;
        long sumSqLum = 0;
        int darkPixels = 0;
        int midGreyPixels = 0;
        int sampleCount = 0;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int lum = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                sumLum += lum;
                sumSqLum += (long) lum * lum;

                if (lum < 100) {
                    darkPixels++;
                } else if (lum >= 140 && lum <= 200) {
                    midGreyPixels++;
                }
                sampleCount++;
            }
        }

        if (sampleCount == 0) {
            return new ImageQualityAnalysis(ImageQuality.FAIR, Map.of());
        }

        double meanLum = (double) sumLum / sampleCount;
        double variance = ((double) sumSqLum / sampleCount) - (meanLum * meanLum);
        double stdDev = Math.sqrt(Math.max(0.0, variance)); // Contrast
        double inkDensity = (double) darkPixels / sampleCount;
        double bleedEstimate = darkPixels > 0 ? (double) midGreyPixels / darkPixels : 0.0;

        ImageQuality quality;
        if (stdDev < 25.0 || inkDensity > 0.60) {
            quality = ImageQuality.POOR;
        } else if (stdDev < 45.0 || bleedEstimate > 5.0) {
            quality = ImageQuality.FAIR;
        } else {
            quality = ImageQuality.GOOD;
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("contrastStdDev", Math.round(stdDev * 100.0) / 100.0);
        metrics.put("meanLuminance", Math.round(meanLum * 100.0) / 100.0);
        metrics.put("inkDensity", Math.round(inkDensity * 1000.0) / 1000.0);
        metrics.put("bleedEstimate", Math.round(bleedEstimate * 100.0) / 100.0);
        metrics.put("width", width);
        metrics.put("height", height);

        return new ImageQualityAnalysis(quality, metrics);
    }
}
