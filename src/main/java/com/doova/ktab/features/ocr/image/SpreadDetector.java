package com.doova.ktab.features.ocr.image;

import com.doova.ktab.features.ocr.config.OcrProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpreadDetector {

    private final OcrProperties properties;

    /**
     * Detects if an image is a two-page spread and locates the gutter split x coordinate.
     * Returns Optional.of(gutterX) if a spread is confirmed, or Optional.empty() otherwise.
     */
    public Optional<Integer> detectGutter(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        double aspectRatio = (double) width / height;
        double threshold = properties.getSpread().getAspectRatioThreshold();

        if (aspectRatio <= threshold) {
            return Optional.empty(); // Not wide enough to be a spread
        }

        // Search vertical projection profile in central band (e.g. 40% to 60% of width)
        int startX = (int) (width * properties.getSpread().getGutterBandStart());
        int endX = (int) (width * properties.getSpread().getGutterBandEnd());

        if (startX >= endX || endX >= width) {
            return Optional.empty();
        }

        int stepY = Math.max(1, height / 150);
        int[] columnLuminance = new int[endX - startX];

        int minLuminanceIndex = -1;
        int minLuminance = Integer.MAX_VALUE;

        int maxLuminanceIndex = -1;
        int maxLuminance = Integer.MIN_VALUE;

        for (int x = startX; x < endX; x++) {
            int colSum = 0;
            int count = 0;
            for (int y = 0; y < height; y += stepY) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                colSum += (int) (0.299 * r + 0.587 * g + 0.114 * b);
                count++;
            }
            int avgLum = count > 0 ? colSum / count : 128;
            int idx = x - startX;
            columnLuminance[idx] = avgLum;

            if (avgLum < minLuminance) {
                minLuminance = avgLum;
                minLuminanceIndex = x;
            }
            if (avgLum > maxLuminance) {
                maxLuminance = avgLum;
                maxLuminanceIndex = x;
            }
        }

        // In scans, gutters either show as a low-ink white margin valley (high luminance)
        // or a dark shadow fold line (low luminance).
        // If there is a clear shadow fold (minLuminance significantly darker than surrounding), pick minLuminanceIndex.
        // Otherwise, pick the brightest column (whitespace gutter) near the center.
        int center = (startX + endX) / 2;
        int chosenGutterX;

        // Check if dark fold exists (at least 30 units darker than median)
        int midLum = (minLuminance + maxLuminance) / 2;
        if (maxLuminance - minLuminance > 40 && minLuminance < 100) {
            chosenGutterX = minLuminanceIndex;
        } else if (maxLuminanceIndex != -1) {
            chosenGutterX = maxLuminanceIndex;
        } else {
            chosenGutterX = center;
        }

        log.debug("Spread detected for image ({}x{}, ratio: {:.2f}) with gutter at x={}",
                width, height, aspectRatio, chosenGutterX);
        return Optional.of(chosenGutterX);
    }
}
