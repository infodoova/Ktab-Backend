# Sub-Plan 06: Render and Reader (PDF + Web Flipbook)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn approved storybook illustrations and checked Arabic text into a finished 21×21 cm square RTL PDF book via headless Chromium (Playwright for Java) and an in-app RTL flipbook reader manifest with signed download access.

---

## Tasks

| # | Task | Files |
|---|---|---|
| 1 | PDF renderer interface & Playwright implementation | `render/StorybookPdfRenderer.java`, `render/PlaywrightPdfRenderer.java`, `render/StorybookPdfRendererTest.java` |
| 2 | RTL HTML template & composer | `resources/templates/storybook/book.html`, `render/StorybookHtmlComposer.java`, `render/StorybookHtmlComposerTest.java` |
| 3 | Render step handler & book advancement to READY | `render/RenderPdfHandler.java`, `render/RenderPdfHandlerTest.java` |
| 4 | Reader manifest & download endpoints | `reader/StorybookReaderService.java`, `reader/dto/*`, `web/StorybookController.java`, tests |

---

### Task 1: PDF Renderer Interface and Playwright Implementation

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/render/StorybookPdfRenderer.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/render/PlaywrightPdfRenderer.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/render/StorybookPdfRendererTest.java`

**Interfaces:**
- `StorybookPdfRenderer`:
  `byte[] renderHtml(String htmlContent);`
- `PlaywrightPdfRenderer`: implements `StorybookPdfRenderer`, uses Playwright headless browser to print to 210mm x 210mm PDF.

---

### Task 2: RTL HTML Template and Composer

**Files:**
- Create: `src/main/resources/templates/storybook/book.html`
- Create: `src/main/java/com/doova/ktab/features/storybook/render/StorybookHtmlComposer.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/render/StorybookHtmlComposerTest.java`

**Requirements:**
- 210mm x 210mm page size with `@page { size: 210mm 210mm; margin: 0; }`.
- RTL direction, Arabic typography (Cairo/Amiri Google fonts).
- Cover page (title, child name, cover art).
- Dedication page (parent dedication card).
- Story pages (illustration + text placed in designated textZone TOP or BOTTOM with contrast card).
- Back cover (Ktab branding/logo).

---

### Task 3: Render Step Handler (`JobStep.RENDER_PDF`)

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/render/RenderPdfHandler.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/render/RenderPdfHandlerTest.java`

**Requirements:**
- Idempotent: reuses PDF in R2 if already generated at key `storybook/{bookId}/pdf/v{version}.pdf`.
- Composes HTML from page illustrations and text.
- Renders PDF bytes.
- Stores in `StorybookAssetStore`.
- Transitions `Storybook` to `READY` status via `StorybookStateMachine`.

---

### Task 4: Reader Manifest and PDF Download Endpoints

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/reader/dto/StorybookReaderManifest.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/reader/dto/ReaderPageDetail.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/reader/dto/StorybookDownloadResponse.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/reader/StorybookReaderService.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/reader/StorybookReaderServiceTest.java`, `StorybookControllerTest.java`

**Endpoints:**
- `GET /storybook/books/{bookId}/reader` -> 200 OK with `StorybookReaderManifest`
- `GET /storybook/books/{bookId}/download` -> 200 OK with `StorybookDownloadResponse`
