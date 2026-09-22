package com.doova.ktab.features.ocr.image;

import com.doova.ktab.features.ocr.config.OcrProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrientationPreChecker {

    private final OcrProperties properties;

    public record OrientationResult(int rotationDegrees, boolean suspicious) {}

    /**
     * Checks if the page orientation is suspicious relative to a median aspect ratio.
     */
    public OrientationResult check(BufferedImage image, double medianAspectRatio) {
        if (!properties.getOrientation().getPrecheck().isEnabled()) {
            return new OrientationResult(0, false);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        double pageRatio = (double) width / height;

        // If book is generally portrait (ratio < 0.9) but this page is landscape (ratio > 1.1)
        if (medianAspectRatio < 0.9 && pageRatio > 1.2) {
            log.info("Page aspect ratio ({:.2f}) differs from median ({:.2f}), marking suspicious",
                    pageRatio, medianAspectRatio);
            return new OrientationResult(0, true);
        }

        return new OrientationResult(0, false);
    }

    /**
     * Rotates an image by 0, 90, 180, or 270 degrees clockwise.
     */
    public static BufferedImage rotate(BufferedImage src, int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        if (normalized == 0) {
            return src;
        }

        int width = src.getWidth();
        int height = src.getHeight();

        int newWidth = (normalized == 90 || normalized == 270) ? height : width;
        int newHeight = (normalized == 90 || normalized == 270) ? width : height;

        BufferedImage dest = new BufferedImage(newWidth, newHeight, src.getType() != 0 ? src.getType() : BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dest.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        if (normalized == 90) {
            g2d.translate(height, 0);
            g2d.rotate(Math.toRadians(90));
        } else if (normalized == 180) {
            g2d.translate(width, height);
            g2d.rotate(Math.toRadians(180));
        } else if (normalized == 270) {
            g2d.translate(0, width);
            g2d.rotate(Math.toRadians(270));
        }

        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();

        return dest;
    }
}
