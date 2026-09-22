package com.doova.ktab.features.ocr.image;

import com.doova.ktab.features.ocr.config.OcrProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

@Component
@RequiredArgsConstructor
@Slf4j
public class BorderCropper {

    private final OcrProperties properties;

    // Luminance threshold for dark scanner border (0-255 scale)
    private static final double DARK_THRESHOLD = 50.0;

    public record CroppedResult(
            BufferedImage image,
            int cropTop,
            int cropBottom,
            int cropLeft,
            int cropRight,
            boolean borderCropSkipped
    ) {}

    /**
     * Trims dark scanner bed bands inward from each edge, preserving a safety margin.
     */
    public CroppedResult crop(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        double maxRatio = properties.getImage().getMaxBorderCropRatio();
        int maxCropX = (int) (width * maxRatio);
        int maxCropY = (int) (height * maxRatio);

        int safetyX = Math.max(1, (int) (width * 0.01));
        int safetyY = Math.max(1, (int) (height * 0.01));

        // Scan from top
        int rawTop = 0;
        while (rawTop <= maxCropY && isRowDark(image, rawTop, width)) {
            rawTop++;
        }
        if (rawTop > maxCropY) {
            log.debug("Dark border crop exceeded threshold on top, skipping border crop. (top:{}, max:{})", rawTop, maxCropY);
            return new CroppedResult(image, 0, 0, 0, 0, true);
        }
        int top = Math.max(0, rawTop - safetyY);

        // Scan from bottom
        int rawBottom = 0;
        while (rawBottom <= maxCropY && isRowDark(image, height - 1 - rawBottom, width)) {
            rawBottom++;
        }
        if (rawBottom > maxCropY) {
            log.debug("Dark border crop exceeded threshold on bottom, skipping border crop. (bottom:{}, max:{})", rawBottom, maxCropY);
            return new CroppedResult(image, 0, 0, 0, 0, true);
        }
        int bottom = Math.max(0, rawBottom - safetyY);

        // Scan from left
        int rawLeft = 0;
        while (rawLeft <= maxCropX && isColDark(image, rawLeft, height)) {
            rawLeft++;
        }
        if (rawLeft > maxCropX) {
            log.debug("Dark border crop exceeded threshold on left, skipping border crop. (left:{}, max:{})", rawLeft, maxCropX);
            return new CroppedResult(image, 0, 0, 0, 0, true);
        }
        int left = Math.max(0, rawLeft - safetyX);

        // Scan from right
        int rawRight = 0;
        while (rawRight <= maxCropX && isColDark(image, width - 1 - rawRight, height)) {
            rawRight++;
        }
        if (rawRight > maxCropX) {
            log.debug("Dark border crop exceeded threshold on right, skipping border crop. (right:{}, max:{})", rawRight, maxCropX);
            return new CroppedResult(image, 0, 0, 0, 0, true);
        }
        int right = Math.max(0, rawRight - safetyX);

        int newWidth = width - left - right;
        int newHeight = height - top - bottom;

        if (newWidth <= 100 || newHeight <= 100 || (top == 0 && bottom == 0 && left == 0 && right == 0)) {
            return new CroppedResult(image, 0, 0, 0, 0, false);
        }

        BufferedImage sub = image.getSubimage(left, top, newWidth, newHeight);
        return new CroppedResult(sub, top, bottom, left, right, false);
    }

    private boolean isRowDark(BufferedImage image, int y, int width) {
        long sum = 0;
        int step = Math.max(1, width / 100);
        int count = 0;
        for (int x = 0; x < width; x += step) {
            sum += getLuminance(image.getRGB(x, y));
            count++;
        }
        return (count > 0) && ((double) sum / count < DARK_THRESHOLD);
    }

    private boolean isColDark(BufferedImage image, int x, int height) {
        long sum = 0;
        int step = Math.max(1, height / 100);
        int count = 0;
        for (int y = 0; y < height; y += step) {
            sum += getLuminance(image.getRGB(x, y));
            count++;
        }
        return (count > 0) && ((double) sum / count < DARK_THRESHOLD);
    }

    private int getLuminance(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }
}
