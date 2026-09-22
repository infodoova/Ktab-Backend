package com.doova.ktab.features.ocr.image;

import com.doova.ktab.features.ocr.config.OcrProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class PageRenderer {

    private final OcrProperties properties;

    public record RenderedPage(BufferedImage image, int effectiveDpi, int width, int height) {}

    /**
     * Renders a single PDF page with DPI clamping to bound the longest side
     * between min-long-side-px and max-long-side-px.
     */
    public RenderedPage render(PDDocument doc, PDFRenderer renderer, int pageIndex) throws IOException {
        PDPage page = doc.getPage(pageIndex);
        PDRectangle mediaBox = page.getMediaBox();

        // 72 points per inch in PDF coordinates
        float widthInches = Math.max(1.0f, mediaBox.getWidth() / 72.0f);
        float heightInches = Math.max(1.0f, mediaBox.getHeight() / 72.0f);
        float longSideInches = Math.max(widthInches, heightInches);

        int configuredDpi = properties.getImage().getDpi();
        int minLongSide = properties.getImage().getMinLongSidePx();
        int maxLongSide = properties.getImage().getMaxLongSidePx();

        int effectiveDpi = configuredDpi;
        float expectedLongSidePx = longSideInches * configuredDpi;

        if (expectedLongSidePx < minLongSide) {
            effectiveDpi = Math.max(configuredDpi, (int) Math.ceil(minLongSide / longSideInches));
            log.debug("Page {} MediaBox is small ({:.1f}x{:.1f} in). Clamping DPI up: {} -> {}",
                    pageIndex + 1, widthInches, heightInches, configuredDpi, effectiveDpi);
        } else if (expectedLongSidePx > maxLongSide) {
            effectiveDpi = Math.min(configuredDpi, (int) Math.floor(maxLongSide / longSideInches));
            log.debug("Page {} MediaBox is large ({:.1f}x{:.1f} in). Clamping DPI down: {} -> {}",
                    pageIndex + 1, widthInches, heightInches, configuredDpi, effectiveDpi);
        }

        // Check for embedded raster image if preferred
        if (properties.getImage().isPreferEmbedded()) {
            BufferedImage embedded = extractSingleEmbeddedImage(page);
            if (embedded != null) {
                int longSide = Math.max(embedded.getWidth(), embedded.getHeight());
                if (longSide >= minLongSide && longSide <= maxLongSide * 1.2) {
                    log.debug("Using extracted embedded raster for page {}", pageIndex + 1);
                    return new RenderedPage(embedded, effectiveDpi, embedded.getWidth(), embedded.getHeight());
                }
            }
        }

        BufferedImage img = renderer.renderImageWithDPI(pageIndex, effectiveDpi, ImageType.RGB);
        return new RenderedPage(img, effectiveDpi, img.getWidth(), img.getHeight());
    }

    /**
     * Attempts to extract a single full-page embedded raster image if present.
     */
    private BufferedImage extractSingleEmbeddedImage(PDPage page) {
        try {
            PDResources resources = page.getResources();
            if (resources == null) return null;

            int imageCount = 0;
            PDImageXObject singleImage = null;

            for (COSName name : resources.getXObjectNames()) {
                PDXObject xobject = resources.getXObject(name);
                if (xobject instanceof PDImageXObject img) {
                    imageCount++;
                    singleImage = img;
                    if (imageCount > 1) {
                        return null; // Page has multiple images
                    }
                }
            }

            if (imageCount == 1 && singleImage != null) {
                return singleImage.getImage();
            }
        } catch (Exception e) {
            log.trace("Embedded image extraction skipped: {}", e.getMessage());
        }
        return null;
    }
}
