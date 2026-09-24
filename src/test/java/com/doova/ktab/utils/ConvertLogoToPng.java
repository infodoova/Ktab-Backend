package com.doova.ktab.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import org.junit.jupiter.api.Test;

public class ConvertLogoToPng {

    @Test
    void executeConversion() throws Exception {
        File inputFile = new File("C:/Users/PC/.gemini/antigravity-ide/brain/aefa23a6-f03e-4c30-99d1-cd47a2901e94/.user_uploaded/media_1790152582760.jpg");
        if (!inputFile.exists()) {
            System.err.println("File not found: " + inputFile.getAbsolutePath());
            return;
        }

        BufferedImage src = ImageIO.read(inputFile);
        int w = src.getWidth();
        int h = src.getHeight();
        System.out.println("Image dimensions: " + w + "x" + h);

        int cornerRgb = src.getRGB(0, 0);
        int cornerRed = (cornerRgb >> 16) & 0xFF;
        int cornerGreen = (cornerRgb >> 8) & 0xFF;
        int cornerBlue = cornerRgb & 0xFF;
        System.out.printf("Corner color: R=%d, G=%d, B=%d\n", cornerRed, cornerGreen, cornerBlue);

        // Create transparent ARGB image
        BufferedImage transparentPng = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // If pixel is near the light grey/white background (threshold distance < 25)
                double dist = Math.sqrt(Math.pow(r - cornerRed, 2) + Math.pow(g - cornerGreen, 2) + Math.pow(b - cornerBlue, 2));
                if (dist < 28) {
                    // Transparent
                    transparentPng.setRGB(x, y, 0x00000000);
                } else if (dist < 45) {
                    // Smooth antialiased boundary
                    int alpha = (int) (((dist - 28) / (45 - 28)) * 255);
                    int blended = (alpha << 24) | (r << 16) | (g << 8) | b;
                    transparentPng.setRGB(x, y, blended);
                } else {
                    transparentPng.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }

        File outDir1 = new File("src/main/resources/static/images");
        outDir1.mkdirs();
        File outFile1 = new File(outDir1, "logo.png");
        ImageIO.write(transparentPng, "png", outFile1);
        System.out.println("Saved transparent PNG to: " + outFile1.getAbsolutePath() + " (" + outFile1.length() + " bytes)");

        // Also save original opaque PNG version
        BufferedImage plainPng = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = plainPng.createGraphics();
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        File outFile2 = new File(outDir1, "logo-opaque.png");
        ImageIO.write(plainPng, "png", outFile2);
        System.out.println("Saved opaque PNG to: " + outFile2.getAbsolutePath() + " (" + outFile2.length() + " bytes)");
    }
}
