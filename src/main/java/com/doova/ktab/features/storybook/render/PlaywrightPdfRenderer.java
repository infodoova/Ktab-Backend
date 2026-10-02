package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.image.ImageDownscaler;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class PlaywrightPdfRenderer implements AutoCloseable {

    private static final String FONT = "storybook/fonts/Cairo.ttf";

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
                Files.copy(font, dir.resolve("fonts/Cairo.ttf"));
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
        if (browser != null && !browser.isConnected()) {
            // Chromium can die under memory pressure or a driver crash; without this the singleton would
            // stay wedged on the dead handle and every render job would fail until the pod is restarted.
            // Just drop the reference rather than calling browser.close(): max-concurrent may be >1, so
            // another thread's render() can still be mid-call on this same (now-dead) object, and its own
            // Playwright calls will already fail and retry on their own once the process is actually gone
            // — explicitly closing it here would only race that thread for no benefit. The Playwright
            // driver process itself (`playwright`) is unaffected by a Chromium crash, so it's kept and
            // reused for the relaunch below instead of being torn down too.
            log.warn("storybook Playwright browser disconnected; relaunching");
            browser = null;
        }
        if (browser == null) {
            try {
                if (playwright == null) {
                    playwright = Playwright.create();
                }
                // Our own trusted template and images only; the container user cannot use Chromium's sandbox.
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setChromiumSandbox(false)
                        .setArgs(List.of("--disable-dev-shm-usage")));
            } catch (RuntimeException e) {
                closeQuietly(); // don't leak a half-started driver process; let the next call try fully fresh
                throw e;
            }
        }
        return browser;
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (browser != null) {
            try {
                browser.close();
            } catch (RuntimeException e) {
                log.warn("storybook Playwright browser close failed", e);
            }
            browser = null;
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (RuntimeException e) {
                log.warn("storybook Playwright driver close failed", e);
            }
            playwright = null;
        }
    }
}
