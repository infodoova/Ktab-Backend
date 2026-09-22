package com.doova.ktab.features.ocr.image;

import com.doova.ktab.features.ocr.config.OcrProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class BorderCropperTest {

    private BorderCropper cropper;

    @BeforeEach
    void setUp() {
        OcrProperties properties = new OcrProperties();
        cropper = new BorderCropper(properties);
    }

    @Test
    @DisplayName("crop should trim dark scanner borders while preserving white paper area")
    void crop_darkBorders_trimsEdges() {
        int width = 500;
        int height = 700;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Fill entire image with white paper
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        // Add 30px black scanner bed on top and left (~4-6% of dimension, within 15% limit)
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, 30);
        g.fillRect(0, 0, 30, height);
        g.dispose();

        BorderCropper.CroppedResult result = cropper.crop(img);

        assertFalse(result.borderCropSkipped());
        assertTrue(result.cropTop() >= 15);
        assertTrue(result.cropLeft() >= 15);
        assertTrue(result.image().getWidth() < width);
        assertTrue(result.image().getHeight() < height);
    }

    @Test
    @DisplayName("crop should skip cropping if dark border exceeds max ratio")
    void crop_massiveBlackMargin_skipsCropping() {
        int width = 400;
        int height = 600;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Fill image mostly with black (30% border > 15% max ratio)
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, 150); // 150 / 600 = 25% > 15%
        g.dispose();

        BorderCropper.CroppedResult result = cropper.crop(img);

        assertTrue(result.borderCropSkipped());
        assertEquals(width, result.image().getWidth());
        assertEquals(height, result.image().getHeight());
    }
}
