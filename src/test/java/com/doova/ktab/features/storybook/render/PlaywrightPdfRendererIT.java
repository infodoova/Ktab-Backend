package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.enums.TextZone;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Needs Chromium (Playwright downloads it on first run). Run: STORYBOOK_RENDER_TESTS=true */
@EnabledIfEnvironmentVariable(named = "STORYBOOK_RENDER_TESTS", matches = "true")
class PlaywrightPdfRendererIT {

    private static final PlaywrightPdfRenderer RENDERER =
            new PlaywrightPdfRenderer(StorybookHtmlBuilderTest.builder(), 1);

    @AfterAll
    static void close() {
        RENDERER.close();
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2048, 2048, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void rendersOneSquarePagePerBookPage() throws Exception {
        BookRenderModel model = RenderModelFactory.build("يومي الأول", "سامي", "<b>إلى بطلنا</b>", TashkeelLevel.FULL, List.of(
                new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ سامي إِلَى المَدْرَسَةِ.", TextZone.BOTTOM),
                new RenderModelFactory.PageSource(2, PageKind.STORY, "لَعِبَ سامي بِالكُرَةِ.", TextZone.TOP)));

        byte[] pdf = RENDERER.render(model, Map.of(0, png(), 1, png(), 2, png()));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(5); // cover, dedication, 2 story, back
            float side = doc.getPage(0).getMediaBox().getWidth();
            assertThat(side).isCloseTo(595.3f, org.assertj.core.data.Offset.offset(1.5f)); // 21 cm in points
            assertThat(doc.getPage(0).getMediaBox().getHeight()).isCloseTo(side, org.assertj.core.data.Offset.offset(0.5f));
        }
        assertThat(pdf.length).isLessThan(6_000_000);
    }
}
