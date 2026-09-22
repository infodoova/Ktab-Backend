package com.doova.ktab.features.ocr.image;

import com.doova.ktab.enums.book.ImageQuality;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class ImageQualityAnalyzerTest {

    private final ImageQualityAnalyzer analyzer = new ImageQualityAnalyzer();

    @Test
    @DisplayName("analyze should classify clear text image with high contrast as GOOD")
    void analyze_clearHighContrastImage_returnsGood() {
        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 300);

        // Add dark text bands
        g.setColor(Color.BLACK);
        g.fillRect(50, 50, 200, 20);
        g.fillRect(50, 100, 200, 20);
        g.dispose();

        ImageQualityAnalyzer.ImageQualityAnalysis result = analyzer.analyze(img);

        assertEquals(ImageQuality.GOOD, result.quality());
        assertTrue(result.metrics().containsKey("contrastStdDev"));
        assertTrue(result.metrics().containsKey("inkDensity"));
    }

    @Test
    @DisplayName("analyze should classify flat low-contrast faded image as POOR")
    void analyze_fadedLowContrastImage_returnsPoor() {
        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // Flat gray image with virtually zero variance
        g.setColor(new Color(150, 150, 150));
        g.fillRect(0, 0, 300, 300);
        g.dispose();

        ImageQualityAnalyzer.ImageQualityAnalysis result = analyzer.analyze(img);

        assertEquals(ImageQuality.POOR, result.quality());
    }
}
