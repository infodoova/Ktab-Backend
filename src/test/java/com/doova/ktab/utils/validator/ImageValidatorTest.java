package com.doova.ktab.utils.validator;

import com.doova.ktab.config.image.ImageValidationProperties;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.ImageValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageValidatorTest {

    private ImageValidator imageValidator;
    private ImageValidationProperties properties;
    private ResourceBundleMessageSource messageSource;

    @BeforeEach
    void setUp() {
        properties = new ImageValidationProperties();
        properties.setCoverRatio(1.6);
        properties.setCoverTolerance(0.25);
        properties.setSquareRatio(1.0);
        properties.setSquareTolerance(0.20);
        properties.setMaxFileSize(5L * 1024 * 1024);
        properties.setMaxPixelCount(40_000_000L);

        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages");
        messageSource.setDefaultEncoding("UTF-8");

        imageValidator = new ImageValidator(properties, messageSource);
    }

    private MockMultipartFile createMockImage(String filename, String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        byte[] bytes = baos.toByteArray();
        String contentType = "image/" + format.toLowerCase();
        return new MockMultipartFile("file", filename, contentType, bytes);
    }

    @Test
    @DisplayName("validateCover with exact ratio 1.6 should pass")
    void validateCover_exactRatio_success() throws Exception {
        MockMultipartFile file = createMockImage("cover.png", "png", 1000, 1600);
        assertDoesNotThrow(() -> imageValidator.validateCover(file));
    }

    @Test
    @DisplayName("validateCover with ratio 1.45 (within increased 0.25 tolerance) should pass")
    void validateCover_ratio145WithinTolerance_success() throws Exception {
        MockMultipartFile file = createMockImage("cover.png", "png", 1000, 1450);
        assertDoesNotThrow(() -> imageValidator.validateCover(file));
    }

    @Test
    @DisplayName("validateCover with ratio 1.80 (within increased 0.25 tolerance) should pass")
    void validateCover_ratio180WithinTolerance_success() throws Exception {
        MockMultipartFile file = createMockImage("cover.png", "png", 1000, 1800);
        assertDoesNotThrow(() -> imageValidator.validateCover(file));
    }

    @Test
    @DisplayName("validateCover with ratio 1.20 (outside tolerance) should fail with detailed ratio message and metadata")
    void validateCover_ratioOutsideTolerance_throwsImageValidationExceptionWithDetails() throws Exception {
        MockMultipartFile file = createMockImage("cover.png", "png", 1000, 1200);

        ImageValidationException ex = assertThrows(
                ImageValidationException.class,
                () -> imageValidator.validateCover(file)
        );

        assertEquals(ApiMessageKey.IMAGE_INVALID_RATIO_COVER, ex.getMessageKey());
        assertNotNull(ex.getDetails());
        assertEquals("1.20", ex.getDetails().get("actualRatio"));
        assertEquals("1.60", ex.getDetails().get("targetRatio"));
        assertEquals("0.25", ex.getDetails().get("tolerance"));
        assertEquals("1.35", ex.getDetails().get("allowedRatioMin"));
        assertEquals("1.85", ex.getDetails().get("allowedRatioMax"));
        assertEquals("1000", ex.getDetails().get("width"));
        assertTrue(ex.getMessage().contains("1.20"));
        assertTrue(ex.getMessage().contains("1000×1200"));
        org.junit.jupiter.api.Assertions.assertFalse(ex.getMessage().contains("{0}"));
        org.junit.jupiter.api.Assertions.assertFalse(ex.getMessage().contains("{1}"));
    }

    @Test
    @DisplayName("validateCover with dimensions 1080x1350 populates all message placeholders without {0}")
    void validateCover_withDimensions1080x1350_populatesAllPlaceholders() throws Exception {
        MockMultipartFile file = createMockImage("cover.png", "png", 1080, 1350);

        ImageValidationException ex = assertThrows(
                ImageValidationException.class,
                () -> imageValidator.validateCover(file)
        );

        assertEquals("1.25", ex.getDetails().get("actualRatio"));
        assertEquals("1.60", ex.getDetails().get("targetRatio"));
        assertEquals("0.25", ex.getDetails().get("tolerance"));
        assertEquals("1080", ex.getDetails().get("width"));
        assertEquals("1350", ex.getDetails().get("height"));

        // Ensure no placeholders remain in message
        org.junit.jupiter.api.Assertions.assertFalse(ex.getMessage().contains("{0}"));
        org.junit.jupiter.api.Assertions.assertFalse(ex.getMessage().contains("{1}"));
        assertTrue(ex.getMessage().contains("1.25"));
        assertTrue(ex.getMessage().contains("1080×1350"));
        assertTrue(ex.getMessage().contains("1.60"));
        assertTrue(ex.getMessage().contains("1.35"));
        assertTrue(ex.getMessage().contains("1.85"));
        assertTrue(ex.getMessage().contains("0.25"));
    }

    @Test
    @DisplayName("validateSquareImage with ratio 0.85 (within increased 0.20 tolerance) should pass")
    void validateSquareImage_ratio085WithinTolerance_success() throws Exception {
        MockMultipartFile file = createMockImage("square.png", "png", 1000, 850);
        assertDoesNotThrow(() -> imageValidator.validateSquareImage(file));
    }

    @Test
    @DisplayName("validateSquareImage with ratio 1.15 (within increased 0.20 tolerance) should pass")
    void validateSquareImage_ratio115WithinTolerance_success() throws Exception {
        MockMultipartFile file = createMockImage("square.png", "png", 1000, 1150);
        assertDoesNotThrow(() -> imageValidator.validateSquareImage(file));
    }

    @Test
    @DisplayName("validateSquareImage with ratio 1.40 (outside tolerance) should fail with detailed square ratio metadata")
    void validateSquareImage_ratioOutsideTolerance_throwsImageValidationExceptionWithDetails() throws Exception {
        MockMultipartFile file = createMockImage("story.png", "png", 1000, 1400);

        ImageValidationException ex = assertThrows(
                ImageValidationException.class,
                () -> imageValidator.validateSquareImage(file)
        );

        assertEquals(ApiMessageKey.IMAGE_INVALID_RATIO_SQUARE, ex.getMessageKey());
        assertNotNull(ex.getDetails());
        assertEquals("1.40", ex.getDetails().get("actualRatio"));
        assertEquals("1.00", ex.getDetails().get("targetRatio"));
        assertEquals("0.20", ex.getDetails().get("tolerance"));
        assertEquals("0.80", ex.getDetails().get("allowedRatioMin"));
        assertEquals("1.20", ex.getDetails().get("allowedRatioMax"));
        assertEquals("1000", ex.getDetails().get("width"));
        assertEquals("1400", ex.getDetails().get("height"));
        assertTrue(ex.getMessage().contains("1.40"));
        assertTrue(ex.getMessage().contains("1000×1400"));
    }

    @Test
    @DisplayName("validateCover with empty file should throw IMAGE_EMPTY with detailed reason")
    void validateCover_emptyFile_throwsImageValidationException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "cover.png", "image/png", new byte[0]);

        ImageValidationException ex = assertThrows(
                ImageValidationException.class,
                () -> imageValidator.validateCover(emptyFile)
        );

        assertEquals(ApiMessageKey.IMAGE_EMPTY, ex.getMessageKey());
        assertEquals("IMAGE_EMPTY", ex.getDetails().get("error"));
    }

    @Test
    @DisplayName("validateCover with blank filename should throw IMAGE_INVALID_FILENAME")
    void validateCover_blankFilename_throwsImageValidationException() {
        MockMultipartFile file = new MockMultipartFile("file", "   ", "image/png", new byte[]{1, 2, 3});

        ImageValidationException ex = assertThrows(
                ImageValidationException.class,
                () -> imageValidator.validateCover(file)
        );

        assertEquals(ApiMessageKey.IMAGE_INVALID_FILENAME, ex.getMessageKey());
        assertEquals("IMAGE_INVALID_FILENAME", ex.getDetails().get("error"));
    }

    @Test
    @DisplayName("validateCover with unsupported format should throw IMAGE_INVALID_FORMAT with allowed formats")
    void validateCover_unsupportedExtension_throwsImageValidationException() {
        MockMultipartFile file = new MockMultipartFile("file", "cover.gif", "image/gif", new byte[]{1, 2, 3});

        ImageValidationException ex = assertThrows(
                ImageValidationException.class,
                () -> imageValidator.validateCover(file)
        );

        assertEquals(ApiMessageKey.IMAGE_INVALID_FORMAT, ex.getMessageKey());
        assertEquals("gif", ex.getDetails().get("detectedFormat"));
        assertTrue(ex.getDetails().get("allowedFormats").contains("PNG"));
    }

    @Test
    @DisplayName("validateCover with size exceeding maximum should throw IMAGE_SIZE_EXCEEDED with formatted sizes")
    void validateCover_sizeExceeded_throwsImageValidationExceptionWithFormattedSizes() {
        properties.setMaxFileSize(100);
        MockMultipartFile largeFile = new MockMultipartFile("file", "cover.png", "image/png", new byte[200]);

        ImageValidationException ex = assertThrows(
                ImageValidationException.class,
                () -> imageValidator.validateCover(largeFile)
        );

        assertEquals(ApiMessageKey.IMAGE_SIZE_EXCEEDED, ex.getMessageKey());
        assertEquals("200 B", ex.getDetails().get("actualSize"));
        assertEquals("100 B", ex.getDetails().get("maxSize"));
    }

    @Test
    @DisplayName("validateCover with corrupted image stream should throw IMAGE_CORRUPTED")
    void validateCover_corruptedImageBytes_throwsImageValidationException() {
        byte[] fakeCorruptedBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        MockMultipartFile corruptedFile = new MockMultipartFile("file", "cover.png", "image/png", fakeCorruptedBytes);

        ImageValidationException ex = assertThrows(
                ImageValidationException.class,
                () -> imageValidator.validateCover(corruptedFile)
        );

        assertEquals(ApiMessageKey.IMAGE_CORRUPTED, ex.getMessageKey());
        assertEquals("IMAGE_UNREADABLE", ex.getDetails().get("error"));
    }
}
