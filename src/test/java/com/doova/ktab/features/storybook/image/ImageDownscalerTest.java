package com.doova.ktab.features.storybook.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageDownscalerTest {

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void shrinksA2kPngTo1024Jpeg() throws Exception {
        byte[] jpeg = ImageDownscaler.toJpeg(png(2048, 2048), 1024);
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(result.getWidth()).isEqualTo(1024);
        assertThat(result.getHeight()).isEqualTo(1024);
        assertThat(jpeg[0]).isEqualTo((byte) 0xFF);
        assertThat(jpeg[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void neverUpscales() throws Exception {
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(ImageDownscaler.toJpeg(png(300, 200), 1024)));
        assertThat(result.getWidth()).isEqualTo(300);
        assertThat(result.getHeight()).isEqualTo(200);
    }
}
