package com.doova.ktab.utils.validator;

import com.doova.ktab.enums.message.ApiMessageKey;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
public class ImageValidator {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PIXEL_COUNT = 40_000_000L;

    // Height / width = 1.6, e.g. 1000 x 1600 portrait cover.
    private static final double COVER_RATIO = 1.6;
    private static final double COVER_TOLERANCE = 0.1;

    private static final double SQUARE_RATIO = 1.0;
    private static final double SQUARE_TOLERANCE = 0.05;

    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "jpg", "png");

    public void validateCover(MultipartFile file) throws IOException {
        validateImage(file, COVER_RATIO, COVER_TOLERANCE);
    }

    public void validateSquareImage(MultipartFile file) throws IOException {
        validateImage(file, SQUARE_RATIO, SQUARE_TOLERANCE);
    }

    private void validateImage(
            MultipartFile file,
            double targetRatio,
            double tolerance
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(ApiMessageKey.IMAGE_INVALID_FILE.getKey());
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(ApiMessageKey.IMAGE_SIZE_EXCEEDED.getKey());
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(ApiMessageKey.IMAGE_INVALID_FILE.getKey());
        }

        String extension = getExtension(fileName);
        if (!ALLOWED_FORMATS.contains(extension)) {
            throw new IllegalArgumentException(ApiMessageKey.IMAGE_INVALID_FORMAT.getKey());
        }

        try (InputStream inputStream = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(inputStream)) {

            if (imageInput == null) {
                throw new IllegalArgumentException(ApiMessageKey.IMAGE_INVALID_FILE.getKey());
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException(ApiMessageKey.IMAGE_INVALID_FILE.getKey());
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);

                String detectedFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!isExpectedFormat(extension, detectedFormat)) {
                    throw new IllegalArgumentException(ApiMessageKey.IMAGE_INVALID_FORMAT.getKey());
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXEL_COUNT) {
                    throw new IllegalArgumentException(ApiMessageKey.IMAGE_INVALID_FILE.getKey());
                }

                double ratio = (double) height / width;
                if (Math.abs(ratio - targetRatio) > tolerance) {
                    throw new IllegalArgumentException(ApiMessageKey.IMAGE_INVALID_RATIO.getKey());
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isExpectedFormat(String extension, String detectedFormat) {
        if ("png".equals(extension)) {
            return "png".equals(detectedFormat);
        }
        return ("jpg".equals(extension) || "jpeg".equals(extension))
                && ("jpg".equals(detectedFormat) || "jpeg".equals(detectedFormat));
    }
}
