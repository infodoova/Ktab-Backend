package com.doova.ktab.features.trailer.endcard;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Ktab end card as transparent 1920x1080 PNG layers with Chromium (correct Arabic shaping, same
 * design on every trailer). Runs once per trailer launch, so it uses a short-lived browser rather than a second
 * long-lived Chromium next to the storybook renderer.
 */
@Component
public class EndCardRenderer {

    public List<Path> render(EndCardSpec spec, Path outDir) {
        Path dir = null;
        try {
            Files.createDirectories(outDir);
            dir = Files.createTempDirectory("endcard-");
            Files.createDirectories(dir.resolve("fonts"));
            try (InputStream font = new ClassPathResource("trailer/endcard/fonts/Cairo.ttf").getInputStream()) {
                Files.copy(font, dir.resolve("fonts/Cairo.ttf"));
            }
            if (spec.hasCover()) {
                Files.copy(spec.coverOrNull(), dir.resolve("cover.jpg"));
            }
            writeTrimmed(spec.logo(), dir.resolve("logo.png"));

            List<Path> written = new ArrayList<>();
            Object ruleBox = null;
            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                         .setHeadless(true)
                         .setChromiumSandbox(false)
                         .setArgs(List.of("--disable-dev-shm-usage")));
                 BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                         .setViewportSize(1920, 1080))) {
                context.route("**/*", route -> {
                    if (route.request().url().startsWith("file:")) {
                        route.resume();
                    } else {
                        route.abort();
                    }
                });
                for (EndCardHtml.Layer layer : EndCardHtml.layersFor(spec)) {
                    Path html = dir.resolve(layer.name().toLowerCase() + ".html");
                    Files.writeString(html, EndCardHtml.build(spec, layer));
                    Page page = context.newPage();
                    page.navigate(html.toUri().toString());
                    page.waitForLoadState();
                    page.waitForFunction("window.__fitted === true");
                    if (layer == EndCardHtml.Layer.TITLE && Boolean.TRUE.equals(page.evaluate("window.__overflow"))) {
                        throw new IllegalStateException(
                                "The book title does not fit the end card even at the minimum size: " + spec.title());
                    }
                    if (layer == EndCardHtml.Layer.RULE) {
                        ruleBox = page.evaluate("(() => { const r = document.querySelector('.rule').getBoundingClientRect();"
                                + " return [Math.floor(r.x), Math.floor(r.y), Math.ceil(r.width), Math.ceil(r.height)]; })()");
                    }
                    Path png = outDir.resolve(layer.fileName());
                    page.screenshot(new Page.ScreenshotOptions().setOmitBackground(true).setPath(png));
                    page.close();
                    written.add(png);
                }
            }
            if (ruleBox instanceof List<?> box && box.size() == 4) {
                // where the rule sits, so the agent can crop it and animate it drawing outwards from its centre
                Path layout = outDir.resolve("layout.json");
                Files.writeString(layout, String.format("{\"rule\":{\"x\":%s,\"y\":%s,\"w\":%s,\"h\":%s}}",
                        box.get(0), box.get(1), box.get(2), box.get(3)));
                written.add(layout);
            }
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (dir != null) {
                FileSystemUtils.deleteRecursively(dir.toFile());
            }
        }
    }

    /** The logo file carries transparent padding; crop to its visible pixels so the 72 px height is the real mark. */
    static void writeTrimmed(Path source, Path target) throws IOException {
        BufferedImage img = ImageIO.read(source.toFile());
        if (img == null) {
            Files.copy(source, target);
            return;
        }
        int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 8) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            Files.copy(source, target);
            return;
        }
        ImageIO.write(img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1), "png", target.toFile());
    }
}
