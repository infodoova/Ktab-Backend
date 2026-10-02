package com.doova.ktab.features.storybook.illustration;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** A character sheet must sit on plain white; a tinted or busy backdrop is redrawn instead of being passed on to every page. */
class SheetBackgroundCheckTest {

    static byte[] png(Color background, Color subject) throws Exception {
        BufferedImage img = new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, 300, 400);
        if (subject != null) {
            g.setColor(subject);
            g.fillOval(90, 100, 120, 220); // a figure in the middle, away from the border
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void aWhiteBackdropWithAFigureInTheMiddleIsFine() throws Exception {
        assertThat(SheetBackgroundCheck.isPlainWhite(png(Color.WHITE, new Color(250, 220, 40)))).isTrue();
    }

    @Test
    void aNearWhiteBackdropIsFine() throws Exception {
        assertThat(SheetBackgroundCheck.isPlainWhite(png(new Color(248, 248, 246), new Color(40, 120, 90)))).isTrue();
    }

    @Test
    void aYellowBackdropIsNotFine() throws Exception {
        assertThat(SheetBackgroundCheck.isPlainWhite(png(new Color(250, 235, 90), new Color(40, 160, 90)))).isFalse();
    }

    @Test
    void aDarkOrBusyBackdropIsNotFine() throws Exception {
        assertThat(SheetBackgroundCheck.isPlainWhite(png(new Color(60, 70, 90), Color.WHITE))).isFalse();
        assertThat(SheetBackgroundCheck.isPlainWhite(png(new Color(210, 225, 245), null))).isFalse(); // pale blue is still a colour
    }

    @Test
    void aPictureThatCannotBeReadIsNotRejectedBecauseItCannotBeJudged() {
        assertThat(SheetBackgroundCheck.isPlainWhite(new byte[]{9})).isTrue();
        assertThat(SheetBackgroundCheck.isPlainWhite(null)).isTrue();
    }
}
