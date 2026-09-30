package com.doova.ktab.utils.validator;

import com.doova.ktab.config.image.ImageValidationProperties;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.ImageValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Validates uploaded images against size, dimensions, format, and aspect ratio tolerances.
 */
@Component
public class ImageValidator {

    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "jpg", "png");

    private final ImageValidationProperties properties;
    private final MessageSource messageSource;

    @Autowired
    public ImageValidator(ImageValidationProperties properties, MessageSource messageSource) {
        this.properties = properties != null ? properties : new ImageValidationProperties();
        this.messageSource = messageSource;
    }

    /**
     * Validates book cover image adhering to cover ratio and tolerance.
     *
     * @param file Uploaded multipart file.
     * @throws IOException If file streaming fails.
     */
    public void validateCover(MultipartFile file) throws IOException {
        validateImage(
                file,
                properties.getCoverRatio(),
                properties.getCoverTolerance(),
                ApiMessageKey.IMAGE_INVALID_RATIO_COVER,
                "COVER"
        );
    }

    /**
     * Validates square image (e.g. story cover, avatar) adhering to square ratio and tolerance.
     *
     * @param file Uploaded multipart file.
     * @throws IOException If file streaming fails.
     */
    public void validateSquareImage(MultipartFile file) throws IOException {
        validateImage(
                file,
                properties.getSquareRatio(),
                properties.getSquareTolerance(),
                ApiMessageKey.IMAGE_INVALID_RATIO_SQUARE,
                "SQUARE"
        );
    }

    private void validateImage(
            MultipartFile file,
            double targetRatio,
            double tolerance,
            ApiMessageKey invalidRatioKey,
            String imageType
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            String msg = resolveMessage(ApiMessageKey.IMAGE_EMPTY);
            throw new ImageValidationException(ApiMessageKey.IMAGE_EMPTY, new Object[0], msg, Map.of(
                    "error", "IMAGE_EMPTY",
                    "reason", "Image file is empty or missing"
            ));
        }

        long maxFileSize = properties.getMaxFileSize();
        if (file.getSize() > maxFileSize) {
            String actualSizeFormatted = formatFileSize(file.getSize());
            String maxSizeFormatted = formatFileSize(maxFileSize);
            Object[] args = new Object[]{actualSizeFormatted, maxSizeFormatted};
            String msg = resolveMessage(ApiMessageKey.IMAGE_SIZE_EXCEEDED, args);
            throw new ImageValidationException(ApiMessageKey.IMAGE_SIZE_EXCEEDED, args, msg, Map.of(
                    "error", "IMAGE_SIZE_EXCEEDED",
                    "actualSize", actualSizeFormatted,
                    "maxSize", maxSizeFormatted
            ));
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            String msg = resolveMessage(ApiMessageKey.IMAGE_INVALID_FILENAME);
            throw new ImageValidationException(ApiMessageKey.IMAGE_INVALID_FILENAME, new Object[0], msg, Map.of(
                    "error", "IMAGE_INVALID_FILENAME",
                    "reason", "Filename is missing or blank"
            ));
        }

        String extension = getExtension(fileName);
        if (!ALLOWED_FORMATS.contains(extension)) {
            String allowedStr = String.join(", ", ALLOWED_FORMATS).toUpperCase(Locale.ROOT);
            String displayExt = extension.isBlank() ? "unknown" : extension;
            Object[] args = new Object[]{displayExt, allowedStr};
            String msg = resolveMessage(ApiMessageKey.IMAGE_INVALID_FORMAT, args);
            throw new ImageValidationException(ApiMessageKey.IMAGE_INVALID_FORMAT, args, msg, Map.of(
                    "error", "IMAGE_INVALID_FORMAT",
                    "detectedFormat", displayExt,
                    "allowedFormats", allowedStr
            ));
        }

        try (InputStream inputStream = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(inputStream)) {

            if (imageInput == null) {
                String msg = resolveMessage(ApiMessageKey.IMAGE_CORRUPTED);
                throw new ImageValidationException(ApiMessageKey.IMAGE_CORRUPTED, new Object[0], msg, Map.of(
                        "error", "IMAGE_CORRUPTED",
                        "reason", "Unable to create image input stream"
                ));
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                String msg = resolveMessage(ApiMessageKey.IMAGE_CORRUPTED);
                throw new ImageValidationException(ApiMessageKey.IMAGE_CORRUPTED, new Object[0], msg, Map.of(
                        "error", "IMAGE_UNREADABLE",
                        "reason", "No suitable image reader found for file data"
                ));
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);

                String detectedFormat = reader.getFormatName() != null
                        ? reader.getFormatName().toLowerCase(Locale.ROOT)
                        : "unknown";

                if (!isExpectedFormat(extension, detectedFormat)) {
                    Object[] args = new Object[]{detectedFormat, extension};
                    String msg = resolveMessage(ApiMessageKey.IMAGE_FORMAT_MISMATCH, args);
                    throw new ImageValidationException(ApiMessageKey.IMAGE_FORMAT_MISMATCH, args, msg, Map.of(
                            "error", "IMAGE_FORMAT_MISMATCH",
                            "detectedContentFormat", detectedFormat,
                            "fileExtension", extension
                    ));
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0) {
                    Object[] args = new Object[]{String.valueOf(width), String.valueOf(height)};
                    String msg = resolveMessage(ApiMessageKey.IMAGE_INVALID_DIMENSIONS, args);
                    throw new ImageValidationException(ApiMessageKey.IMAGE_INVALID_DIMENSIONS, args, msg, Map.of(
                            "error", "IMAGE_INVALID_DIMENSIONS",
                            "width", String.valueOf(width),
                            "height", String.valueOf(height)
                    ));
                }

                long maxPixelCount = properties.getMaxPixelCount();
                long totalPixels = (long) width * height;
                if (totalPixels > maxPixelCount) {
                    long actualMp = Math.round((double) totalPixels / 1_000_000.0);
                    long maxMp = Math.round((double) maxPixelCount / 1_000_000.0);
                    Object[] args = new Object[]{String.valueOf(width), String.valueOf(height), actualMp, maxMp};
                    String msg = resolveMessage(ApiMessageKey.IMAGE_DIMENSIONS_EXCEEDED, args);
                    throw new ImageValidationException(ApiMessageKey.IMAGE_DIMENSIONS_EXCEEDED, args, msg, Map.of(
                            "error", "IMAGE_DIMENSIONS_EXCEEDED",
                            "width", String.valueOf(width),
                            "height", String.valueOf(height),
                            "actualMegapixels", String.valueOf(actualMp),
                            "maxMegapixels", String.valueOf(maxMp)
                    ));
                }

                double ratio = (double) height / width;
                double diff = Math.abs(ratio - targetRatio);
                if (diff > tolerance) {
                    double minRatio = Math.max(0.01, targetRatio - tolerance);
                    double maxRatio = targetRatio + tolerance;

                    String formattedActualRatio = String.format(Locale.ROOT, "%.2f", ratio);
                    String formattedTargetRatio = String.format(Locale.ROOT, "%.2f", targetRatio);
                    String formattedMinRatio = String.format(Locale.ROOT, "%.2f", minRatio);
                    String formattedMaxRatio = String.format(Locale.ROOT, "%.2f", maxRatio);
                    String formattedTolerance = String.format(Locale.ROOT, "%.2f", tolerance);

                    Object[] args = new Object[]{
                            formattedActualRatio,
                            String.valueOf(width),
                            String.valueOf(height),
                            formattedTargetRatio,
                            formattedMinRatio,
                            formattedMaxRatio,
                            formattedTolerance
                    };

                    String msg = resolveMessage(invalidRatioKey, args);

                    Map<String, String> details = new LinkedHashMap<>();
                    details.put("error", invalidRatioKey.name());
                    details.put("imageType", imageType);
                    details.put("actualRatio", formattedActualRatio);
                    details.put("targetRatio", formattedTargetRatio);
                    details.put("tolerance", formattedTolerance);
                    details.put("allowedRatioMin", formattedMinRatio);
                    details.put("allowedRatioMax", formattedMaxRatio);
                    details.put("width", String.valueOf(width));
                    details.put("height", String.valueOf(height));

                    throw new ImageValidationException(invalidRatioKey, args, msg, details);
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private String resolveMessage(ApiMessageKey key, Object... args) {
        String resolved = null;
        if (messageSource != null) {
            try {
                resolved = messageSource.getMessage(key.getKey(), args, LocaleContextHolder.getLocale());
            } catch (Exception ignored) {
            }
        }
        if (resolved == null) {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle("messages", LocaleContextHolder.getLocale());
                resolved = bundle.getString(key.getKey());
            } catch (Exception ignored) {
                resolved = key.getKey();
            }
        }
        // If the resolved message contains unpopulated placeholders like {0}, populate them with MessageFormat!
        if (resolved != null && resolved.contains("{0}") && args != null && args.length > 0) {
            try {
                resolved = MessageFormat.format(resolved, args);
            } catch (Exception ignored) {
            }
        }
        return resolved;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        } else {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        String raw = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        // Strip CDN / resize modifiers appended after the real extension, e.g.
        // "photo.jpg!w700wp"  ->  "jpg"
        // "image.png@2x"      ->  "png"
        // Only keep leading alphabetic characters.
        int end = 0;
        while (end < raw.length() && Character.isLetter(raw.charAt(end))) {
            end++;
        }
        return end > 0 ? raw.substring(0, end) : raw;
    }

    private boolean isExpectedFormat(String extension, String detectedFormat) {
        if ("png".equals(extension)) {
            return "png".equals(detectedFormat);
        }
        return ("jpg".equals(extension) || "jpeg".equals(extension))
                && ("jpg".equals(detectedFormat) || "jpeg".equals(detectedFormat));
    }
}
