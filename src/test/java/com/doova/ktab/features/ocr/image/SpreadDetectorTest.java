package com.doova.ktab.features.ocr.image;

import com.doova.ktab.features.ocr.config.OcrProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SpreadDetectorTest {

    private SpreadDetector detector;

    @BeforeEach
    void setUp() {
        OcrProperties props = new OcrProperties();
        detector = new SpreadDetector(props);
    }

    @Test
    @DisplayName("detectGutter should return empty for portrait single pages")
    void detectGutter_portraitSinglePage_returnsEmpty() {
        BufferedImage portrait = new BufferedImage(500, 700, BufferedImage.TYPE_INT_RGB);
        Optional<Integer> gutter = detector.detectGutter(portrait);
        assertTrue(gutter.isEmpty());
    }

    @Test
    @DisplayName("detectGutter should detect central gutter line on wide two-page spread")
    void detectGutter_twoPageSpread_detectsGutter() {
        int width = 1000;
        int height = 600; // ratio 1.66 > 1.2
        BufferedImage spread = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = spread.createGraphics();

        // White paper with text content on left and right
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        // Dark gutter line at x=500
        g.setColor(Color.DARK_GRAY);
        g.fillRect(495, 0, 10, height);
        g.dispose();

        Optional<Integer> gutter = detector.detectGutter(spread);
        assertTrue(gutter.isPresent());
        assertTrue(gutter.get() >= 480 && gutter.get() <= 520);
    }
}
