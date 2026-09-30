package com.doova.ktab.features.trailer.endcard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Needs Chromium (Playwright downloads it on first run). Run: TRAILER_ENDCARD_TESTS=true */
@EnabledIfEnvironmentVariable(named = "TRAILER_ENDCARD_TESTS", matches = "true")
class EndCardRendererIT {

    private static final Path LOGO = Path.of("src/main/resources/trailer/endcard/ktab-logo.png");

    private final EndCardRenderer renderer = new EndCardRenderer();

    private static Path sampleCover(Path dir) throws Exception {
        BufferedImage img = new BufferedImage(400, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(122, 74, 40), 400, 600, new Color(40, 24, 16)));
        g.fillRect(0, 0, 400, 600);
        g.dispose();
        Path cover = dir.resolve("cover-src.jpg");
        ImageIO.write(img, "jpg", cover.toFile());
        return cover;
    }

    private static boolean hasOpaquePixelsIn(BufferedImage img, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y += 4) {
            for (int x = x0; x < x1; x += 4) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 200) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int opaqueRows(BufferedImage img) {
        int rows = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 200) {
                    rows++;
                    break;
                }
            }
        }
        return rows;
    }

    @Test
    void rendersEveryLayerAsATransparentFullHdPng(@TempDir Path tmp) throws Exception {
        EndCardSpec spec = new EndCardSpec("ثورة دونالد ترامب: حتمية التغيير", "ألكسندر دوغين", sampleCover(tmp), LOGO);

        List<Path> layers = renderer.render(spec, tmp.resolve("out"));

        assertThat(layers).extracting(p -> p.getFileName().toString())
                .containsExactly("scrim.png", "cover.png", "title.png", "subtitle.png", "rule.png", "author.png",
                        "logo.png", "layout.json");
        for (Path p : layers.stream().filter(x -> x.toString().endsWith(".png")).toList()) {
            BufferedImage img = ImageIO.read(p.toFile());
            assertThat(img.getWidth()).isEqualTo(1920);
            assertThat(img.getHeight()).isEqualTo(1080);
            assertThat(img.getColorModel().hasAlpha()).as(p.getFileName().toString()).isTrue();
        }
        BufferedImage title = ImageIO.read(tmp.resolve("out/title.png").toFile());
        assertThat((title.getRGB(10, 10) >>> 24) & 0xFF).as("corner is transparent").isZero();
        assertThat(hasOpaquePixelsIn(title, 930, 300, 1730, 780)).as("title text is drawn").isTrue();
        BufferedImage logoImg = ImageIO.read(tmp.resolve("out/logo.png").toFile());
        assertThat(hasOpaquePixelsIn(logoImg, 900, 900, 1020, 990)).as("logo is bottom centre").isTrue();
        BufferedImage cover = ImageIO.read(tmp.resolve("out/cover.png").toFile());
        assertThat(hasOpaquePixelsIn(cover, 240, 260, 640, 820)).as("cover is on the left").isTrue();

        // the rule position the agent needs to animate the line drawing
        var rule = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(tmp.resolve("out/layout.json").toFile()).path("rule");
        assertThat(rule.path("w").asInt()).isEqualTo(350);
        assertThat(rule.path("h").asInt()).isBetween(2, 3);
        assertThat(rule.path("x").asInt()).isEqualTo(1155); // column centre 1330 - 175

        Files.createDirectories(Path.of("target/endcard-layers"));
        for (Path p : layers) {
            Files.copy(p, Path.of("target/endcard-layers").resolve(p.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        writePreview(layers.stream().filter(x -> x.toString().endsWith(".png")).toList(), tmp.resolve("out"));
    }

    @Test
    void aVeryLongTitleFitsInThreeLinesAndIsNotCutOff(@TempDir Path tmp) throws Exception {
        String longTitle = "عنوان طويل جدا لكتاب يتجاوز الحد المعتاد من الأحرف في سطر واحد ".repeat(2).strip();
        renderer.render(new EndCardSpec(longTitle, "م", sampleCover(tmp), LOGO), tmp.resolve("out"));

        BufferedImage title = ImageIO.read(tmp.resolve("out/title.png").toFile());
        // 3 lines at the 32 px floor: 3 * 32 * 1.35 = 130 px of line boxes, so the ink spans well under 140 rows
        assertThat(opaqueRows(title)).isBetween(20, 140);
    }

    @Test
    void anImpossibleTitleFailsLoudlyInsteadOfShippingACutOffCard(@TempDir Path tmp) throws Exception {
        String absurd = "كلمةطويلةجدا ".repeat(40);
        assertThatThrownBy(() -> renderer.render(new EndCardSpec(absurd, "م", sampleCover(tmp), LOGO), tmp.resolve("out")))
                .hasMessageContaining("does not fit");
    }

    @Test
    void noCoverAndNoAuthorStillRender(@TempDir Path tmp) {
        List<Path> layers = renderer.render(new EndCardSpec("عنوان", null, null, LOGO), tmp.resolve("out"));
        assertThat(layers).extracting(p -> p.getFileName().toString()).containsExactly("scrim.png", "title.png", "logo.png");
    }

    /** Composite over mid-grey for a manual visual sign-off of the design (target/endcard-preview.png). */
    private static void writePreview(List<Path> layers, Path out) throws Exception {
        BufferedImage bg = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bg.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(90, 96, 110), 1920, 1080, new Color(150, 130, 100)));
        g.fillRect(0, 0, 1920, 1080);
        for (Path p : layers) {
            g.drawImage(ImageIO.read(p.toFile()), 0, 0, null);
        }
        g.dispose();
        Files.createDirectories(Path.of("target"));
        ImageIO.write(bg, "png", Path.of("target/endcard-preview.png").toFile());
    }
}
