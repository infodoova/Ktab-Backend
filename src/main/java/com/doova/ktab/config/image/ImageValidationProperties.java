package com.doova.ktab.config.image;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration properties for image validation.
 */
@Data
@Configuration
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "app.image.validation")
public class ImageValidationProperties {

    /**
     * Target ratio (height / width) for portrait book cover images (e.g. 1.6).
     */
    private double coverRatio = 1.6;

    /**
     * Tolerance for cover image aspect ratio.
     * Default: 0.25 (allowed range: [coverRatio - coverTolerance, coverRatio + coverTolerance]).
     */
    private double coverTolerance = 0.25;

    /**
     * Target ratio (height / width) for square images like avatars and story covers (1.0).
     */
    private double squareRatio = 1.0;

    /**
     * Tolerance for square image aspect ratio.
     * Default: 0.20 (allowed range: [squareRatio - squareTolerance, squareRatio + squareTolerance]).
     */
    private double squareTolerance = 0.20;

    /**
     * Maximum allowed image file size in bytes.
     * Default: 5MB (5 * 1024 * 1024).
     */
    private long maxFileSize = 5L * 1024 * 1024;

    /**
     * Maximum allowed total pixel count (width * height).
     * Default: 40,000,000 pixels (40 megapixels).
     */
    private long maxPixelCount = 40_000_000L;
}
