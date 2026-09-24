package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.image.ImageDownscaler;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Component
public class PlaywrightPdfRenderer implements AutoCloseable {

    private static final String FONT = "storybook/fonts/NotoNaskhArabic-Regular.ttf";

    private final StorybookHtmlBuilder htmlBuilder;
    private final Semaphore slots;
    private Playwright playwright;
    private Browser browser;

    public PlaywrightPdfRenderer(StorybookHtmlBuilder htmlBuilder,
                                 @Value("${ktab.storybook.render.max-concurrent:1}") int maxConcurrent) {
        this.htmlBuilder = htmlBuilder;
        this.slots = new Semaphore(maxConcurrent);
    }

    public byte[] render(BookRenderModel model, Map<Integer, byte[]> imagesByPageIndex) {
        slots.acquireUninterruptibly();
        Path dir = null;
        try {
            dir = Files.createTempDirectory("storybook-render-");
            Files.createDirectories(dir.resolve("fonts"));
            Files.createDirectories(dir.resolve("img"));
            try (InputStream font = new ClassPathResource(FONT).getInputStream()) {
                Files.copy(font, dir.resolve("fonts/NotoNaskhArabic-Regular.ttf"));
            }
            for (Map.Entry<Integer, byte[]> e : imagesByPageIndex.entrySet()) {
                Files.write(dir.resolve(RenderModelFactory.imageFile(e.getKey())), ImageDownscaler.toJpeg(e.getValue(), 2048));
            }
            Path html = dir.resolve("book.html");
            Files.writeString(html, htmlBuilder.build(model));

            try (BrowserContext context = browser().newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
                context.route("**/*", route -> {
                    if (route.request().url().startsWith("file:")) {
                        route.resume();
                    } else {
                        route.abort();
                    }
                });
                Page page = context.newPage();
                page.navigate(html.toUri().toString());
                page.waitForLoadState();
                return page.pdf(new Page.PdfOptions()
                        .setWidth("21cm")
                        .setHeight("21cm")
                        .setPrintBackground(true)
                        .setPreferCSSPageSize(true));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (dir != null) {
                FileSystemUtils.deleteRecursively(dir.toFile());
            }
            slots.release();
        }
    }

    private synchronized Browser browser() {
        if (browser == null) {
            playwright = Playwright.create();
            // Our own trusted template and images only; the container user cannot use Chromium's sandbox.
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setChromiumSandbox(false)
                    .setArgs(List.of("--disable-dev-shm-usage")));
        }
        return browser;
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}
