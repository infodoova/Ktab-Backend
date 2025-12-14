package com.doova.ktab.utils.validator;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

@Component
public class ImageValidator {

    private static final double TARGET_RATIO = 1.6;     // height : width
    private static final double RATIO_TOLERANCE = 0.1; // ±1%
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    public void validateCover(MultipartFile file) throws IOException {

        // 1. File type validation
        String fileName = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        if (!(fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png"))) {
            throw new IllegalArgumentException("صيغة الغلاف يجب أن تكون JPG أو PNG فقط.");
        }

        // 2. File size validation
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("حجم ملف الغلاف يجب ألا يتجاوز 5MB.");
        }

        // 3. Read image dimensions
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new IllegalArgumentException("ملف الصورة غير صالح.");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // 4. Ratio validation
        double ratio = (double) height / width;
        double minAllowed = TARGET_RATIO - RATIO_TOLERANCE;
        double maxAllowed = TARGET_RATIO + RATIO_TOLERANCE;

        if (ratio < minAllowed || ratio > maxAllowed) {
            throw new IllegalArgumentException(
                    String.format("نسبة أبعاد الغلاف يجب أن تكون 1.6:1 (النسبة الحالية: %.3f)", ratio)
            );
        }
    }

    // 5. Dominant color extraction (simple average method)
    public String extractDominantColor(MultipartFile file) throws IOException {

        BufferedImage image = ImageIO.read(file.getInputStream());
        long r = 0, g = 0, b = 0;
        int w = image.getWidth();
        int h = image.getHeight();
        long total = (long) w * h;

        for (int x = 0; x < w; x += 10) { // sample every 10px for speed
            for (int y = 0; y < h; y += 10) {
                int rgb = image.getRGB(x, y);
                r += (rgb >> 16) & 0xFF;
                g += (rgb >> 8) & 0xFF;
                b += rgb & 0xFF;
            }
        }

        int avgR = (int) (r / (total / 100));
        int avgG = (int) (g / (total / 100));
        int avgB = (int) (b / (total / 100));

        return String.format("#%02x%02x%02x", avgR, avgG, avgB);
    }
}