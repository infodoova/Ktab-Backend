package com.doova.ktab.utils.validator;

import com.doova.ktab.enums.ApiMessageKey;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

@Component
public class ImageValidator {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    // Cover image → 1.6 (e.g. 16:10)
    private static final double COVER_RATIO = 1.6;
    private static final double COVER_TOLERANCE = 0.1;

    // Square image → 1:1
    private static final double SQUARE_RATIO = 1.0;
    private static final double SQUARE_TOLERANCE = 0.05;

    /* =========================
       Public validators
       ========================= */

    public void validateCover(MultipartFile file) throws IOException {
        validateImage(
                file,
                COVER_RATIO,
                COVER_TOLERANCE,
                ApiMessageKey.IMAGE_INVALID_RATIO
        );
    }

    public void validateSquareImage(MultipartFile file) throws IOException {
        validateImage(
                file,
                SQUARE_RATIO,
                SQUARE_TOLERANCE,
                ApiMessageKey.IMAGE_INVALID_RATIO
        );
    }

    /* =========================
       Shared logic
       ========================= */

    private void validateImage(
            MultipartFile file,
            double targetRatio,
            double tolerance,
            ApiMessageKey ratioErrorKey
    ) throws IOException {

        String fileName = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();

        if (!(fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png"))) {
            throw new IllegalArgumentException(ApiMessageKey.IMAGE_INVALID_FORMAT.getKey());
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(ApiMessageKey.IMAGE_SIZE_EXCEEDED.getKey());
        }

        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new IllegalArgumentException(ApiMessageKey.IMAGE_INVALID_FILE.getKey());
        }

        double ratio = (double) image.getHeight() / image.getWidth();
        double min = targetRatio - tolerance;
        double max = targetRatio + tolerance;

        if (ratio < min || ratio > max) {
            throw new IllegalArgumentException(ratioErrorKey.getKey());
        }
    }
}
