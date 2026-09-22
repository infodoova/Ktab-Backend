package com.doova.ktab.features.ocr.image;

import com.doova.ktab.enums.book.ReadingDirection;
import com.doova.ktab.enums.book.SpreadSide;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class SpreadSplitter {

    public record SplitPage(BufferedImage image, SpreadSide side) {}

    /**
     * Splits a spread image at gutterX and orders the resulting pages by reading direction.
     * For RTL books (Arabic), the RIGHT half is earlier and comes first.
     * For LTR books, the LEFT half is earlier and comes first.
     */
    public List<SplitPage> split(BufferedImage image, int gutterX, ReadingDirection readingDirection) {
        int width = image.getWidth();
        int height = image.getHeight();

        int leftWidth = Math.max(1, gutterX);
        int rightWidth = Math.max(1, width - gutterX);

        BufferedImage leftHalf = image.getSubimage(0, 0, leftWidth, height);
        BufferedImage rightHalf = image.getSubimage(gutterX, 0, rightWidth, height);

        List<SplitPage> result = new ArrayList<>(2);

        if (readingDirection == ReadingDirection.RTL) {
            // For RTL: right half is read first (lower page number)
            result.add(new SplitPage(rightHalf, SpreadSide.RIGHT));
            result.add(new SplitPage(leftHalf, SpreadSide.LEFT));
        } else {
            // For LTR: left half is read first (lower page number)
            result.add(new SplitPage(leftHalf, SpreadSide.LEFT));
            result.add(new SplitPage(rightHalf, SpreadSide.RIGHT));
        }

        log.debug("Split spread ({}x{}) at x={} into two pages with direction {}",
                width, height, gutterX, readingDirection);
        return result;
    }
}
