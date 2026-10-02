package com.doova.ktab.features.storybook.illustration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * Every page is drawn from the character sheet, so a sheet on a tinted or busy backdrop spreads that colour into the whole
 * book. The model sometimes ignores "plain white background"; this is the objective check that catches it.
 */
final class SheetBackgroundCheck {

    private static final double BORDER = 0.04;
    private static final int STEP = 4;
    private static final int MIN_CHANNEL = 225;
    private static final int MAX_SPREAD = 18;
    private static final double REQUIRED_SHARE = 0.9;

    private SheetBackgroundCheck() {
    }

    /** True when the border of the picture is near-white. A picture that cannot be read cannot be judged, so it is not rejected. */
    static boolean isPlainWhite(byte[] image) {
        if (image == null) {
            return true;
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(image));
        } catch (Exception e) {
            return true;
        }
        if (img == null) {
            return true;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int bx = Math.max(1, (int) (w * BORDER));
        int by = Math.max(1, (int) (h * BORDER));
        int samples = 0;
        int white = 0;
        for (int y = 0; y < h; y += STEP) {
            for (int x = 0; x < w; x += STEP) {
                boolean onBorder = x < bx || x >= w - bx || y < by || y >= h - by;
                if (!onBorder) {
                    continue;
                }
                samples++;
                if (isNearWhite(img.getRGB(x, y))) {
                    white++;
                }
            }
        }
        return samples == 0 || (double) white / samples >= REQUIRED_SHARE;
    }

    private static boolean isNearWhite(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int min = Math.min(r, Math.min(g, b));
        int max = Math.max(r, Math.max(g, b));
        return min >= MIN_CHANNEL && max - min <= MAX_SPREAD;
    }
}
