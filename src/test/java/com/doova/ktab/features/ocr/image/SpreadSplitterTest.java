package com.doova.ktab.features.ocr.image;

import com.doova.ktab.enums.book.ReadingDirection;
import com.doova.ktab.enums.book.SpreadSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpreadSplitterTest {

    private final SpreadSplitter splitter = new SpreadSplitter();

    @Test
    @DisplayName("split for RTL should return right half first with SpreadSide.RIGHT")
    void split_rtlDirection_rightHalfIsFirst() {
        BufferedImage spread = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_RGB);

        List<SpreadSplitter.SplitPage> pages = splitter.split(spread, 500, ReadingDirection.RTL);

        assertEquals(2, pages.size());
        assertEquals(SpreadSide.RIGHT, pages.get(0).side());
        assertEquals(500, pages.get(0).image().getWidth());

        assertEquals(SpreadSide.LEFT, pages.get(1).side());
        assertEquals(500, pages.get(1).image().getWidth());
    }

    @Test
    @DisplayName("split for LTR should return left half first with SpreadSide.LEFT")
    void split_ltrDirection_leftHalfIsFirst() {
        BufferedImage spread = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_RGB);

        List<SpreadSplitter.SplitPage> pages = splitter.split(spread, 500, ReadingDirection.LTR);

        assertEquals(2, pages.size());
        assertEquals(SpreadSide.LEFT, pages.get(0).side());
        assertEquals(SpreadSide.RIGHT, pages.get(1).side());
    }
}
