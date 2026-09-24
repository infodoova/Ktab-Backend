# Storybook 06 — Render and Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Read `2026-09-24-storybook-00-overview.md` first — its "Shared contracts" and "Global Constraints" sections apply to every task here.

**Goal:** Turn a book whose pages all passed QA into a 21×21 cm RTL PDF stored in R2, expose a reader manifest for the in-app flipbook, and hand out a short-lived download link.

**Architecture:** A Thymeleaf template (`templates/storybook/book.html`) lays out cover, dedication, story pages and back page with the Arabic text typeset over each illustration's empty zone. `PlaywrightPdfRenderer` writes the HTML, a bundled Arabic font and JPEG-compressed images into a temp folder and prints it with headless Chromium — the spec's choice because Chromium shapes Arabic and handles bidi correctly. The `RENDER_PDF` step handler stores the PDF at a deterministic key and moves the book to `READY`. The reader manifest reuses the same page model, so the flipbook and the PDF always show the same text.

**Tech Stack:** `com.microsoft.playwright:playwright` (Java), Thymeleaf (already in Ktab), PDFBox (already in Ktab — used only to *verify* PDFs in tests, never to render), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-24-personalized-storybook-spec.md`

## Global Constraints

See the overview. Most relevant here:

- Output: 21×21 cm square PDF, no bleed; web preview: RTL flipbook.
- A book is: cover, dedication page, 10/12/15 story pages, back page.
- Render with Chromium via Playwright; never PDFBox/JasperReports/xhtml2pdf for rendering.
- Arabic text is typeset in HTML, never drawn by the image model. The tashkeel level is applied at render time (D3).
- The only parent-written text is the dedication; it must never be interpreted as markup.

## Review Focus

Owned by this sub-plan: **#5 a dedication containing HTML or script (`<img src=x onerror=…>`).** Expected: printed literally as text in the PDF and the reader; nothing executes in Chromium. Tests: Task 2, `StorybookHtmlBuilderTest.escapesDedication`; Task 3, Chromium is launched with JavaScript disabled and every non-`file:` request aborted.

Also here: **#4 another user's PDF by id** — Task 5, `ReaderServiceTest.otherUsersBookIsNotFound`.

## File structure

```
pom.xml                                                            (Task 1: playwright)
src/main/resources/storybook/fonts/NotoNaskhArabic-Regular.ttf     (Task 1: font, OFL)
src/main/resources/templates/storybook/book.html                   (Task 2)
src/main/java/com/doova/ktab/features/storybook/render/
├── RenderPage.java  BookRenderModel.java  RenderModelFactory.java  (Task 2)
├── StorybookHtmlBuilder.java                                       (Task 2)
├── PlaywrightPdfRenderer.java                                      (Task 3)
├── RenderPersistence.java  RenderPdfHandler.java                   (Task 4)
└── ReaderService.java  ReaderManifest.java                         (Task 5)
src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java (Task 5)
Dockerfile                                                          (Task 6)
```

---

### Task 1: Playwright dependency and the Arabic font

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/storybook/fonts/NotoNaskhArabic-Regular.ttf`, `src/main/resources/storybook/fonts/OFL.txt`

**Interfaces:**
- Produces: `com.microsoft.playwright.*` on the classpath; the font at `storybook/fonts/NotoNaskhArabic-Regular.ttf`.

- [x] **Step 1: Add the dependency**

Add to `pom.xml` `<dependencies>`:
```xml
        <dependency>
            <groupId>com.microsoft.playwright</groupId>
            <artifactId>playwright</artifactId>
            <version>1.49.0</version>
        </dependency>
```
Bump to the newest 1.x release available when you implement (check with `./mvnw -q versions:display-dependency-updates -Dincludes=com.microsoft.playwright:playwright`); the Dockerfile in Task 6 reads the version from the resolved jar, so only this line changes.

- [x] **Step 2: Add the font**

Download Noto Naskh Arabic (Regular) from Google Fonts (`https://fonts.google.com/noto/specimen/Noto+Naskh+Arabic`, SIL Open Font License 1.1), and copy `NotoNaskhArabic-Regular.ttf` and its `OFL.txt` into `src/main/resources/storybook/fonts/`. The OFL allows embedding in PDFs; keep `OFL.txt` next to the font.

- [x] **Step 3: Verify**

Run: `./mvnw -q -DskipTests compile && ls src/main/resources/storybook/fonts/`
Expected: BUILD SUCCESS; both files listed.

- [x] **Step 4: Commit**

```bash
git add pom.xml src/main/resources/storybook/fonts
git commit -m "build(storybook): add Playwright and the Noto Naskh Arabic font"
```

---

### Task 2: Render model, template and HTML builder

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/render/RenderPage.java`, `BookRenderModel.java`, `RenderModelFactory.java`, `StorybookHtmlBuilder.java`
- Create: `src/main/resources/templates/storybook/book.html`
- Test: `src/test/java/com/doova/ktab/features/storybook/render/StorybookHtmlBuilderTest.java`, `RenderModelFactoryTest.java`

**Interfaces:**
- Consumes: `TashkeelFilter` (01); `Storybook`, `StorybookPage`, `PageKind`, `TextZone`, `TashkeelLevel` (02); Thymeleaf `ITemplateEngine` (Ktab's `SpringTemplateEngine` bean).
- Produces:
  - `enum RenderPage.Kind { COVER, DEDICATION, STORY, BACK }`; `record RenderPage(int order, Kind kind, String textAr, TextZone textZone, String imageFile)` — `imageFile` is a relative file name (`img/p3.jpg`) or null; `textAr` already has the tashkeel level applied.
  - `record BookRenderModel(String titleAr, String childNameAr, List<RenderPage> pages)`.
  - `RenderModelFactory.build(String titleAr, String childNameAr, String dedication, TashkeelLevel level, List<PageSource> sources) : BookRenderModel` with `record PageSource(int pageIndex, PageKind kind, String textAr, TextZone textZone)`. Order: cover (index 0) → dedication → story pages by index → back. Image file for page index `i` is `img/p<i>.jpg`.
  - Fixed texts (MSA, shown in every variety): dedication fallback `إلى <name>، بكل الحب.`; back page `النهاية` and `كُتبت هذه القصة خصيصًا لـ<name>.`. The tashkeel level is applied to them too.
  - `StorybookHtmlBuilder.build(BookRenderModel model) : String` — renders `storybook/book.html`.

- [x] **Step 1: Write the failing tests**

`RenderModelFactoryTest.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.enums.TextZone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RenderModelFactoryTest {

    private static List<RenderModelFactory.PageSource> sources() {
        return List.of(
                new RenderModelFactory.PageSource(2, PageKind.STORY, "لَعِبَ سامي.", TextZone.BOTTOM),
                new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ سامي.", TextZone.TOP));
    }

    @Test
    void ordersCoverDedicationStoryBack() {
        BookRenderModel m = RenderModelFactory.build("يومي", "سامي", null, TashkeelLevel.FULL, sources());

        assertThat(m.pages()).extracting(RenderPage::kind).containsExactly(
                RenderPage.Kind.COVER, RenderPage.Kind.DEDICATION, RenderPage.Kind.STORY, RenderPage.Kind.STORY, RenderPage.Kind.BACK);
        assertThat(m.pages().get(2).textAr()).isEqualTo("ذَهَبَ سامي.");
        assertThat(m.pages().get(2).imageFile()).isEqualTo("img/p1.jpg");
        assertThat(m.pages().get(0).imageFile()).isEqualTo("img/p0.jpg");
        assertThat(m.pages().get(1).imageFile()).isNull();
    }

    @Test
    void appliesTheTashkeelLevelEverywhere() {
        BookRenderModel m = RenderModelFactory.build("يَوْمِي", "سامي", null, TashkeelLevel.NONE, sources());
        assertThat(m.titleAr()).isEqualTo("يومي");
        assertThat(m.pages().get(2).textAr()).isEqualTo("ذهب سامي.");
        assertThat(m.pages().get(4).textAr()).doesNotContain("ُ");
    }

    @Test
    void parentDedicationWinsOverTheDefault() {
        assertThat(RenderModelFactory.build("t", "سامي", "إلى بطلنا", TashkeelLevel.FULL, sources()).pages().get(1).textAr())
                .isEqualTo("إلى بطلنا");
        assertThat(RenderModelFactory.build("t", "سامي", null, TashkeelLevel.FULL, sources()).pages().get(1).textAr())
                .isEqualTo("إلى سامي، بكل الحب.");
    }
}
```

`StorybookHtmlBuilderTest.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.enums.TextZone;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookHtmlBuilderTest {

    static StorybookHtmlBuilder builder() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return new StorybookHtmlBuilder(engine);
    }

    private static BookRenderModel model(String dedication) {
        return RenderModelFactory.build("يومي الأول", "سامي", dedication, TashkeelLevel.FULL, List.of(
                new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ سامي.", TextZone.BOTTOM)));
    }

    @Test
    void isRightToLeftArabicWithSquarePages() {
        String html = builder().build(model(null));
        assertThat(html).contains("dir=\"rtl\"").contains("lang=\"ar\"").contains("size: 21cm 21cm");
        assertThat(html).contains("NotoNaskhArabic-Regular.ttf");
        assertThat(html.split("class=\"page ", -1)).hasSize(5); // cover, dedication, 1 story, back
    }

    @Test
    void storyTextSitsInItsZone() {
        String html = builder().build(model(null));
        assertThat(html).contains("zone-bottom").contains("ذَهَبَ سامي.").contains("img/p1.jpg");
    }

    @Test
    void escapesDedication() {
        String html = builder().build(model("<img src=x onerror=alert(1)><script>alert(2)</script>"));
        assertThat(html).doesNotContain("<img src=x").doesNotContain("<script>alert(2)");
        assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;");
    }
}
```

- [x] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='RenderModelFactoryTest,StorybookHtmlBuilderTest'`
Expected: COMPILATION ERROR.

- [x] **Step 3: Implement the model and factory**

`RenderPage.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.TextZone;

public record RenderPage(int order, Kind kind, String textAr, TextZone textZone, String imageFile) {
    public enum Kind { COVER, DEDICATION, STORY, BACK }
}
```

`BookRenderModel.java`:
```java
package com.doova.ktab.features.storybook.render;

import java.util.List;

public record BookRenderModel(String titleAr, String childNameAr, List<RenderPage> pages) {
}
```

`RenderModelFactory.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.story.TashkeelFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared by the PDF and the reader manifest so both always show the same text. */
public final class RenderModelFactory {

    public record PageSource(int pageIndex, PageKind kind, String textAr, TextZone textZone) {
    }

    private RenderModelFactory() {
    }

    public static BookRenderModel build(String titleAr, String childNameAr, String dedication, TashkeelLevel level,
                                        List<PageSource> sources) {
        List<PageSource> sorted = sources.stream().sorted(Comparator.comparingInt(PageSource::pageIndex)).toList();
        List<RenderPage> pages = new ArrayList<>();
        int order = 0;
        for (PageSource s : sorted) {
            if (s.kind() == PageKind.COVER) {
                pages.add(new RenderPage(order++, RenderPage.Kind.COVER, null, TextZone.TOP, imageFile(s.pageIndex())));
            }
        }
        String dedicationText = dedication == null || dedication.isBlank()
                ? "إلى " + childNameAr + "، بكل الحب."
                : dedication;
        pages.add(new RenderPage(order++, RenderPage.Kind.DEDICATION, TashkeelFilter.apply(dedicationText, level), null, null));
        for (PageSource s : sorted) {
            if (s.kind() == PageKind.STORY) {
                pages.add(new RenderPage(order++, RenderPage.Kind.STORY, TashkeelFilter.apply(s.textAr(), level),
                        s.textZone(), imageFile(s.pageIndex())));
            }
        }
        String back = "النهاية\nكُتبت هذه القصة خصيصًا لـ" + childNameAr + ".";
        pages.add(new RenderPage(order, RenderPage.Kind.BACK, TashkeelFilter.apply(back, level), null, null));
        return new BookRenderModel(TashkeelFilter.apply(titleAr, level), childNameAr, pages);
    }

    public static String imageFile(int pageIndex) {
        return "img/p" + pageIndex + ".jpg";
    }
}
```

The parent's own dedication is passed through `TashkeelFilter` like everything else; the filter only removes diacritics, so it cannot change the parent's wording.

- [x] **Step 4: Write the template**

`src/main/resources/templates/storybook/book.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" dir="rtl" lang="ar">
<head>
    <meta charset="UTF-8">
    <title th:text="${model.titleAr()}">title</title>
    <style>
        @font-face {
            font-family: "BookNaskh";
            src: url("fonts/NotoNaskhArabic-Regular.ttf") format("truetype");
        }
        @page { size: 21cm 21cm; margin: 0; }
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; }
        body { font-family: "BookNaskh", serif; color: #2b2118; }
        .page {
            width: 21cm; height: 21cm; position: relative; overflow: hidden;
            page-break-after: always; break-after: page;
        }
        .page:last-child { page-break-after: auto; break-after: auto; }
        .art { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
        .text {
            position: absolute; left: 1.4cm; right: 1.4cm;
            font-size: 22pt; line-height: 1.9; text-align: center;
            padding: 0.4cm 0.7cm; border-radius: 0.5cm;
            background: rgba(255, 252, 245, 0.78);
            white-space: pre-line; unicode-bidi: plaintext;
        }
        .zone-top { top: 1.2cm; }
        .zone-bottom { bottom: 1.2cm; }
        .cover-title { font-size: 34pt; line-height: 1.5; }
        .cover-name { font-size: 20pt; margin-top: 0.2cm; }
        .plain { background: #fbf6ec; display: flex; align-items: center; justify-content: center; }
        .plain .message { font-size: 24pt; line-height: 2; text-align: center; padding: 3cm; white-space: pre-line; }
    </style>
</head>
<body>
<th:block th:each="p : ${model.pages()}">
    <section th:if="${p.kind().name() == 'COVER'}" class="page cover">
        <img class="art" th:src="${p.imageFile()}" alt="">
        <div class="text zone-top">
            <div class="cover-title" th:text="${model.titleAr()}">title</div>
            <div class="cover-name" th:text="${model.childNameAr()}">name</div>
        </div>
    </section>
    <section th:if="${p.kind().name() == 'DEDICATION'}" class="page plain dedication">
        <div class="message" th:text="${p.textAr()}">dedication</div>
    </section>
    <section th:if="${p.kind().name() == 'STORY'}" class="page story">
        <img class="art" th:src="${p.imageFile()}" alt="">
        <div th:class="${'text zone-' + #strings.toLowerCase(p.textZone().name())}" th:text="${p.textAr()}">text</div>
    </section>
    <section th:if="${p.kind().name() == 'BACK'}" class="page plain back">
        <div class="message" th:text="${p.textAr()}">the end</div>
    </section>
</th:block>
</body>
</html>
```

`th:text` HTML-escapes its value, which is what keeps a hostile dedication inert.

- [x] **Step 5: Implement the builder**

```java
package com.doova.ktab.features.storybook.render;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

@Component
public class StorybookHtmlBuilder {

    private final ITemplateEngine templateEngine;

    public StorybookHtmlBuilder(ITemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String build(BookRenderModel model) {
        Context ctx = new Context(Locale.forLanguageTag("ar"));
        ctx.setVariable("model", model);
        return templateEngine.process("storybook/book", ctx);
    }
}
```

Ktab's Spring Boot Thymeleaf auto-configuration resolves `storybook/book` to `classpath:templates/storybook/book.html`.

- [x] **Step 6: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='RenderModelFactoryTest,StorybookHtmlBuilderTest'`
Expected: 6 tests PASS.

- [x] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/render src/main/resources/templates/storybook src/test/java/com/doova/ktab/features/storybook/render
git commit -m "feat(storybook): add RTL book template and render model"
```

---

### Task 3: `PlaywrightPdfRenderer`

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/render/PlaywrightPdfRenderer.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/render/PlaywrightPdfRendererIT.java`

**Interfaces:**
- Consumes: `StorybookHtmlBuilder`, `BookRenderModel`, `RenderModelFactory.imageFile` (Task 2); `ImageDownscaler` (01).
- Produces: `byte[] render(BookRenderModel model, Map<Integer, byte[]> imagesByPageIndex)` — writes `book.html`, `fonts/NotoNaskhArabic-Regular.ttf` and `img/p<i>.jpg` (each image re-encoded to JPEG, long side ≤ 2048) into a temp directory, prints a 21×21 cm PDF, deletes the directory. At most `ktab.storybook.render.max-concurrent` (default 1) renders at a time. One Chromium per app instance, started lazily, closed on shutdown. Chromium runs with JavaScript disabled, and any request that is not `file:` is aborted.

- [x] **Step 1: Write the failing test (runs only where Chromium can be installed)**

```java
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
```

Inspect one rendered PDF by eye before trusting the pipeline: temporarily write `pdf` to `target/sample-book.pdf` and open it — the Arabic must be connected (not isolated letters), right-to-left, with tashkeel placed on the right letters.

- [x] **Step 2: Run it to verify it fails**

Run: `STORYBOOK_RENDER_TESTS=true ./mvnw -q test -Dtest=PlaywrightPdfRendererIT`
Expected: COMPILATION ERROR.

- [x] **Step 3: Implement**

```java
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
```

With JavaScript disabled, `document.fonts.ready` cannot be awaited; `waitForLoadState()` (the `load` event) already waits for `@font-face` fonts referenced by rendered text in Chromium. If the visual check in Step 1 ever shows the fallback font, add `page.waitForTimeout(250)` before `pdf(...)` and note why.

- [x] **Step 4: Run it to verify it passes**

Run: `STORYBOOK_RENDER_TESTS=true ./mvnw -q test -Dtest=PlaywrightPdfRendererIT`
Expected: PASS (the first run downloads Chromium into `~/.cache/ms-playwright`).

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/render/PlaywrightPdfRenderer.java src/test/java/com/doova/ktab/features/storybook/render/PlaywrightPdfRendererIT.java
git commit -m "feat(storybook): render 21x21 cm RTL PDFs with headless Chromium"
```

---

### Task 4: `RENDER_PDF` step handler

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/render/RenderContext.java`, `RenderPersistence.java`, `RenderPdfHandler.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/render/RenderPdfHandlerTest.java`, `RenderPersistenceIT.java`

**Interfaces:**
- Consumes: `PlaywrightPdfRenderer`, `RenderModelFactory` (Tasks 2–3); `StorybookAssetStore`, `StorybookKeys` (05); repositories, `StorybookStateMachine` (02, 03).
- Produces:
  - `record RenderContext(Long bookId, StorybookStatus status, String titleAr, String childNameAr, String dedication, TashkeelLevel level, List<RenderModelFactory.PageSource> pages, Map<Integer, String> imageKeysByPageIndex)`.
  - `RenderPersistence.context(Long bookId)` (`readOnly`), `RenderPersistence.finish(Long bookId, String pdfKey)` (`@Transactional`: sets `pdfKey`, `RENDERING → READY`; no-op if not `RENDERING`).
  - `RenderPdfHandler` (`RENDER_PDF`, generation = render round): not `RENDERING` → `success()`; key `pdf(book, round)`; object exists → `finish` without rendering; else load images, render, `put(key, pdf, "application/pdf")`, `finish` → `success()`.

- [x] **Step 1: Write the failing tests**

`RenderPdfHandlerTest.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RenderPdfHandlerTest {

    private final PlaywrightPdfRenderer renderer = mock(PlaywrightPdfRenderer.class);
    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final RenderPersistence persistence = mock(RenderPersistence.class);
    private final RenderPdfHandler handler = new RenderPdfHandler(renderer, store, persistence);

    private static StorybookJob job(int round) {
        StorybookJob j = new StorybookJob();
        j.setStorybookId(9L);
        j.setStep(JobStep.RENDER_PDF);
        j.setGeneration(round);
        return j;
    }

    private static RenderContext ctx(StorybookStatus status) {
        return new RenderContext(9L, status, "يومي", "سامي", null, TashkeelLevel.FULL,
                List.of(new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                        new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ.", TextZone.TOP)),
                Map.of(0, "k0", 1, "k1"));
    }

    @Test
    void rendersStoresAndFinishes() {
        when(persistence.context(9L)).thenReturn(ctx(StorybookStatus.RENDERING));
        when(store.get(anyString())).thenReturn(new byte[]{1});
        when(renderer.render(any(), anyMap())).thenReturn(new byte[]{'%', 'P'});

        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);

        verify(renderer).render(any(), argThat(m -> m.keySet().equals(java.util.Set.of(0, 1))));
        verify(store).put(eq("storybook/9/book-r0.pdf"), any(), eq("application/pdf"));
        verify(persistence).finish(9L, "storybook/9/book-r0.pdf");
    }

    @Test
    void aStoredPdfIsNotRenderedAgain() {
        when(persistence.context(9L)).thenReturn(ctx(StorybookStatus.RENDERING));
        when(store.exists("storybook/9/book-r1.pdf")).thenReturn(true);

        handler.handle(job(1));

        verifyNoInteractions(renderer);
        verify(persistence).finish(9L, "storybook/9/book-r1.pdf");
    }

    @Test
    void staleJobDoesNothing() {
        when(persistence.context(9L)).thenReturn(ctx(StorybookStatus.READY));
        handler.handle(job(0));
        verifyNoInteractions(renderer, store);
    }
}
```

`RenderPersistenceIT.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.story.pipeline.StoryPersistence;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import({RenderPersistence.class, StorybookStateMachine.class, StoryPersistence.class, JobEnqueuer.class})
class RenderPersistenceIT extends StorybookJpaIT {

    @Autowired RenderPersistence persistence;
    @Autowired StoryPersistence stories;

    @Test
    void contextListsCoverAndPagesAndFinishMakesTheBookReady() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "render@example.com"));
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "ذَهَبَ سامي."), 0);
        book.setStatus(StorybookStatus.RENDERING);
        em.flush();

        RenderContext ctx = persistence.context(book.getId());
        assertThat(ctx.pages()).hasSize(11);
        assertThat(ctx.childNameAr()).isEqualTo("سامي");

        persistence.finish(book.getId(), "storybook/x/book-r0.pdf");
        assertThat(book.getStatus()).isEqualTo(StorybookStatus.READY);
        assertThat(book.getPdfKey()).isEqualTo("storybook/x/book-r0.pdf");
    }
}
```

(`imageKeysByPageIndex` is empty here because no images were attached; the handler test covers the mapping.)

- [x] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='RenderPdfHandlerTest,RenderPersistenceIT'`
Expected: COMPILATION ERROR.

- [x] **Step 3: Implement**

`RenderContext.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;

import java.util.List;
import java.util.Map;

public record RenderContext(Long bookId, StorybookStatus status, String titleAr, String childNameAr, String dedication,
                            TashkeelLevel level, List<RenderModelFactory.PageSource> pages,
                            Map<Integer, String> imageKeysByPageIndex) {
}
```

`RenderPersistence.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RenderPersistence {

    private final StorybookRepository books;
    private final StorybookPageRepository pages;
    private final StorybookStateMachine stateMachine;

    @Transactional(readOnly = true)
    public RenderContext context(Long bookId) {
        Storybook book = books.findById(bookId).orElseThrow();
        List<StorybookPage> stored = pages.findByStorybook_IdOrderByPageIndexAsc(bookId);
        Map<Integer, String> keys = new HashMap<>();
        for (StorybookPage p : stored) {
            if (p.getCurrentImage() != null) {
                keys.put((int) p.getPageIndex(), p.getCurrentImage().getImageKey());
            }
        }
        return new RenderContext(bookId, book.getStatus(), book.getTitleAr(), book.getInputs().childNameAr(),
                book.getDedication(), book.getTashkeelLevel(),
                stored.stream().map(p -> new RenderModelFactory.PageSource(p.getPageIndex(), p.getKind(), p.getTextAr(), p.getTextZone())).toList(),
                keys);
    }

    @Transactional
    public void finish(Long bookId, String pdfKey) {
        Storybook book = books.findById(bookId).orElseThrow();
        if (book.getStatus() != StorybookStatus.RENDERING) {
            return;
        }
        book.setPdfKey(pdfKey);
        stateMachine.transition(book, StorybookStatus.READY);
    }
}
```

`RenderPdfHandler.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.storage.StorybookKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RenderPdfHandler implements StepHandler {

    private final PlaywrightPdfRenderer renderer;
    private final StorybookAssetStore store;
    private final RenderPersistence persistence;

    @Override
    public JobStep step() {
        return JobStep.RENDER_PDF;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        RenderContext ctx = persistence.context(job.getStorybookId());
        if (ctx.status() != StorybookStatus.RENDERING) {
            return StepOutcome.success();
        }
        String key = StorybookKeys.pdf(ctx.bookId(), job.getGeneration());
        if (!store.exists(key)) {
            Map<Integer, byte[]> images = new HashMap<>();
            ctx.imageKeysByPageIndex().forEach((index, imageKey) -> images.put(index, store.get(imageKey)));
            BookRenderModel model = RenderModelFactory.build(ctx.titleAr(), ctx.childNameAr(), ctx.dedication(),
                    ctx.level(), ctx.pages());
            store.put(key, renderer.render(model, images), "application/pdf");
        }
        persistence.finish(ctx.bookId(), key);
        return StepOutcome.success();
    }
}
```

- [x] **Step 4: Run them to verify they pass**

Run: `./mvnw -q test -Dtest='RenderPdfHandlerTest,RenderPersistenceIT'`
Expected: 4 tests PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/render src/test/java/com/doova/ktab/features/storybook/render
git commit -m "feat(storybook): add idempotent PDF render step"
```

---

### Task 5: Reader manifest and download link

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/render/ReaderManifest.java`, `ReaderService.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/render/ReaderServiceTest.java`

**Interfaces:**
- Consumes: `StorybookAccessGuard` (02), `RenderPersistence`, `RenderModelFactory` (Tasks 2, 4), `FileStorageService.getFileUrl` / `getPreSignedDownloadUrl` (Ktab).
- Produces:
  - `record ReaderManifest(Long bookId, String dir, String titleAr, List<Page> pages)` with `record Page(int order, RenderPage.Kind kind, String textAr, TextZone textZone, String imageUrl)`; `dir = "rtl"`.
  - `ReaderService.manifest(User owner, Long bookId)` — owner-only (404 otherwise); `READY` only (else 409 `STORYBOOK_NOT_READY`); image URLs signed with `UrlStrategy.SIGNED`.
  - `ReaderService.downloadUrl(User owner, Long bookId) : String` — `READY` with a `pdfKey`; `getPreSignedDownloadUrl(pdfKey, 10 minutes, "storybook-<id>.pdf")`.
  - Endpoints: `GET /storybook/books/{bookId}/reader`, `GET /storybook/books/{bookId}/download` (returns `{"url": "..."}`).

- [x] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReaderServiceTest {

    private final StorybookAccessGuard guard = mock(StorybookAccessGuard.class);
    private final RenderPersistence persistence = mock(RenderPersistence.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final ReaderService service = new ReaderService(guard, persistence, storage);
    private final User owner = new User();
    private final Storybook book = new Storybook();

    @BeforeEach
    void setUp() {
        book.setId(9L);
        book.setStatus(StorybookStatus.READY);
        book.setPdfKey("storybook/9/book-r0.pdf");
        when(guard.requireOwned(9L, owner)).thenReturn(book);
        when(persistence.context(9L)).thenReturn(new RenderContext(9L, StorybookStatus.READY, "يومي", "سامي", null,
                TashkeelLevel.NONE,
                List.of(new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                        new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ سامي.", TextZone.BOTTOM)),
                Map.of(0, "k0", 1, "k1")));
        when(storage.getFileUrl(anyString(), eq(UrlStrategy.SIGNED))).thenAnswer(inv -> "https://signed/" + inv.getArgument(0));
    }

    @Test
    void manifestMatchesThePdfLayout() {
        ReaderManifest m = service.manifest(owner, 9L);
        assertThat(m.dir()).isEqualTo("rtl");
        assertThat(m.pages()).extracting(ReaderManifest.Page::kind).containsExactly(
                RenderPage.Kind.COVER, RenderPage.Kind.DEDICATION, RenderPage.Kind.STORY, RenderPage.Kind.BACK);
        assertThat(m.pages().get(2).textAr()).isEqualTo("ذهب سامي.");
        assertThat(m.pages().get(2).imageUrl()).isEqualTo("https://signed/k1");
        assertThat(m.pages().get(1).imageUrl()).isNull();
    }

    @Test
    void notReadyIsAConflict() {
        book.setStatus(StorybookStatus.ILLUSTRATING);
        assertThatThrownBy(() -> service.manifest(owner, 9L)).isInstanceOf(StorybookStateConflictException.class);
        assertThatThrownBy(() -> service.downloadUrl(owner, 9L)).isInstanceOf(StorybookStateConflictException.class);
    }

    @Test
    void downloadIsShortLived() {
        when(storage.getPreSignedDownloadUrl("storybook/9/book-r0.pdf", Duration.ofMinutes(10), "storybook-9.pdf"))
                .thenReturn("https://download");
        assertThat(service.downloadUrl(owner, 9L)).isEqualTo("https://download");
    }

    @Test
    void otherUsersBookIsNotFound() {
        User stranger = new User();
        when(guard.requireOwned(9L, stranger)).thenThrow(new ResourceNotFoundException(
                com.doova.ktab.enums.message.ApiMessageKey.STORYBOOK_NOT_FOUND));
        assertThatThrownBy(() -> service.downloadUrl(stranger, 9L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storage);
    }
}
```

- [x] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=ReaderServiceTest`
Expected: COMPILATION ERROR.

- [x] **Step 3: Implement**

`ReaderManifest.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.TextZone;

import java.util.List;

public record ReaderManifest(Long bookId, String dir, String titleAr, List<Page> pages) {
    public record Page(int order, RenderPage.Kind kind, String textAr, TextZone textZone, String imageUrl) {
    }
}
```

`ReaderService.java`:
```java
package com.doova.ktab.features.storybook.render;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReaderService {

    private final StorybookAccessGuard guard;
    private final RenderPersistence persistence;
    private final FileStorageService storage;

    @Transactional(readOnly = true)
    public ReaderManifest manifest(User owner, Long bookId) {
        requireReady(guard.requireOwned(bookId, owner));
        RenderContext ctx = persistence.context(bookId);
        BookRenderModel model = RenderModelFactory.build(ctx.titleAr(), ctx.childNameAr(), ctx.dedication(), ctx.level(), ctx.pages());
        List<ReaderManifest.Page> pages = model.pages().stream().map(p -> new ReaderManifest.Page(p.order(), p.kind(),
                p.textAr(), p.textZone(), p.imageFile() == null ? null : imageUrl(ctx, p.imageFile()))).toList();
        return new ReaderManifest(bookId, "rtl", model.titleAr(), pages);
    }

    @Transactional(readOnly = true)
    public String downloadUrl(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        requireReady(book);
        return storage.getPreSignedDownloadUrl(book.getPdfKey(), Duration.ofMinutes(10), "storybook-" + bookId + ".pdf");
    }

    private static void requireReady(Storybook book) {
        if (book.getStatus() != StorybookStatus.READY || book.getPdfKey() == null) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_NOT_READY);
        }
    }

    private String imageUrl(RenderContext ctx, String imageFile) {
        int pageIndex = Integer.parseInt(imageFile.substring("img/p".length(), imageFile.length() - ".jpg".length()));
        String key = ctx.imageKeysByPageIndex().get(pageIndex);
        return key == null ? null : storage.getFileUrl(key, UrlStrategy.SIGNED);
    }
}
```

- [x] **Step 4: Add the endpoints**

In `StorybookController`, add the field `private final com.doova.ktab.features.storybook.render.ReaderService readerService;` (after `pageRegenerationService`) and:
```java
    @GetMapping("/books/{bookId}/reader")
    public ResponseEntity<ApiResponse<com.doova.ktab.features.storybook.render.ReaderManifest>> reader(
            @CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(readerService.manifest(user, bookId),
                ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @GetMapping("/books/{bookId}/download")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> download(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(java.util.Map.of("url", readerService.downloadUrl(user, bookId)),
                ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }
```
Add `mock(ReaderService.class)` as the next argument in `StorybookControllerTest.setUp()`.

- [x] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='ReaderServiceTest,StorybookControllerTest'`
Expected: all PASS.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): add reader manifest and short-lived PDF download"
```

---

### Task 6: Chromium in the Docker image

**Files:**
- Modify: `Dockerfile`

**Interfaces:**
- Produces: a runtime image where `Playwright.create()` finds Chromium at `/ms-playwright` without downloading anything, and the system libraries Chromium needs are installed.

- [x] **Step 1: Edit the build stage**

After `RUN mvn clean package -DskipTests -B` in the builder stage, add:
```dockerfile
# Collect the Playwright jars so the runtime stage can run its installer
RUN mvn -q dependency:copy-dependencies -DincludeGroupIds=com.microsoft.playwright -DoutputDirectory=/build/playwright-jars
```

- [x] **Step 2: Edit the runtime stage**

Replace the runtime stage's `apt-get` block and add the browser install **before** `USER ktab:ktab` (it needs root):
```dockerfile
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

RUN apt-get update && \
    apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl && \
    rm -rf /var/lib/apt/lists/*

# Chromium + its system libraries for the storybook PDF renderer
COPY --from=builder /build/playwright-jars /opt/playwright-jars
RUN java -cp "/opt/playwright-jars/*" com.microsoft.playwright.CLI install --with-deps chromium && \
    chmod -R a+rX /ms-playwright && \
    rm -rf /var/lib/apt/lists/*

# From here on the app must never try to download a browser at runtime
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
```

- [x] **Step 3: Build and smoke-test the image**

Run:
```bash
docker build -t ktab-storybook-test .
docker run --rm --entrypoint sh ktab-storybook-test -c 'ls /ms-playwright && java -cp "/opt/playwright-jars/*" com.microsoft.playwright.CLI --version'
```
Expected: a `chromium-*` directory listed and a Playwright version printed.

Then run the app image with `ktab.storybook.enabled=true` against a dev database, take one book through to `READY`, and open the PDF from `GET /api/v1/storybook/books/{id}/download`.

- [x] **Step 4: Commit**

```bash
git add Dockerfile
git commit -m "build(storybook): bundle Chromium for PDF rendering in the runtime image"
```
