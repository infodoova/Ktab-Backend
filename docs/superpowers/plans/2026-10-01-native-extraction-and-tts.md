# Native Arabic Extraction + Native TTS (Studio switch) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ktab can ingest Arabic PDFs and produce audiobooks without the ElevenLabs Studio API and without OCR. One flag, `KTAB_STUDIO_ENABLED`, switches both jobs. When it is `true`, Studio does the work as today for digital PDFs. When it is `false`, Ktab's own text-layer extraction service and its own TTS pipeline do it. OCR is switched off entirely (owner, 2026-10-01): a second flag, `KTAB_OCR_ENABLED` (default `false`), keeps the OCR code in place but unused, so it can be turned back on later.

**Architecture:**
- **Extraction service:** a new, database-free package `features/extraction` implements the owner's spec: validation, metadata, page-by-page text, cleaning, outline → printed TOC → heading detection, chapters with hierarchy, confidence and warnings. It is exposed at `POST /api/books/extract` and used by a new Spring Batch job, `nativeIngestionJob`.
- **Native ingestion:** `nativeIngestionJob` writes the result into the same tables every pipeline already shares (`tbl_book_pages`, `tbl_book_sections`, `tbl_books`). The reader, search, talk-to-book and the trailer agent therefore work unchanged.
- **Native TTS:** a new `nativeAudiobookJob` splits each chapter into chunks, calls ElevenLabs' **standard** text-to-speech `/with-timestamps` endpoint (which the account can use, unlike Studio), joins the chunks with FFmpeg, and writes the **same** outputs Studio writes: the MP3 and gzipped timing index at the same storage keys, and `tbl_book_audio_chapters` rows. The reader app needs no change.
- **Routing:** `IngestionRouter` and the audiobook endpoint choose the job by the flags. With OCR off, every book that would have gone to `ocrJob` (scanned, hybrid, mixed, unknown) goes to `nativeIngestionJob` instead. A PDF with no usable text layer at all (a fully scanned book) is rejected there with a clear reason, because V1 has no OCR.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Batch, PDFBox 3.0.6 (already in the build), WebClient (already used by `features.tts`), FFmpeg/ffprobe (already in the Docker image), Playwright 1.49 (test-scope fixture generation only), JUnit 5, AssertJ, Mockito.

**Spec:** [`docs/extraction/arabic-book-extraction-spec.md`](../../extraction/arabic-book-extraction-spec.md) (the owner's spec, condensed, plus the switch requirement). Supporting design: `docs/ocr_engine_v3.md` ("Two independent pipelines, one output contract").

## Confirmed by the owner (2026-10-01)

- **OCR is off.** No book is sent to `ocrJob` while `KTAB_OCR_ENABLED=false` (the default).
- **The narrator voice is chosen later by the owner.** There is no default: `KTAB_NATIVE_TTS_VOICE_ID` must be set before the first audiobook, and the job fails fast with a clear message if it is blank.
- **"My own TTS" = ElevenLabs' standard text-to-speech API** (`/v1/text-to-speech/{voice}/with-timestamps`). It's not Studio, so it already works on the account, and it returns per-character timings, which the reader's highlight index needs. The plan isolates it behind a `ChunkTtsProvider` interface, so another vendor can be added later without touching the job.

## Global Constraints

- **The flags:**
  - `ktab.studio.enabled=${KTAB_STUDIO_ENABLED:false}`: `true` sends DIGITAL books → `studioIngestionJob` and audiobooks → `studioAudiobookJob` (current behaviour). `false` sends DIGITAL books → `nativeIngestionJob` and every audiobook → `nativeAudiobookJob`.
  - `ktab.ocr.enabled=${KTAB_OCR_ENABLED:false}`: while `false`, any book whose route resolves to OCR (scanned, hybrid, mixed, unknown, shadow mode, or an admin lock to OCR) goes to `nativeIngestionJob` instead. `ocrJob` is never launched. Setting it to `true` restores today's OCR routing.
- **The Studio code is untouched**, apart from three things: the flag check in the router and the audiobook endpoint, and an early return in `StudioOrphanReconciler` when Studio is off. Turning the flag on again must restore today's behaviour exactly.
- **Output contract shared with OCR and Studio:**
  - `tbl_book_pages`: one row per PDF page, `col_page_number` = PDF page (1-based), `col_source_pdf_page` = the same, `col_markdown_content` = raw text, `col_markdown_clean` = cleaned text.
  - `tbl_book_sections`: a tree with `parent`, `level`, `sort_order`, `start_page`, `end_page`.
  - `tbl_books`: `structure_status`, `structure_source`, `page_count`, `ocr_status = COMPLETED`.
- **Audio contract (identical to Studio):**
  - MP3 at `audio/{bookId}/chapters/ch-{sortOrder:04d}.mp3`.
  - Timing index at `audio/{bookId}/timings/ch-{sortOrder:04d}.json.gz`, in the format `{"v":1,"chapterId":"…","chars":"…","startMs":[…],"endMs":[…]}`, gzipped.
  - One `tbl_book_audio_chapters` row per chapter, and `book.hasAudio = true` at the end.
- **Arabic text rules:** never alter `rawText`. The cleaner only removes whole lines and collapses whitespace: it never changes letters (أ إ آ ة ى ؤ ئ) and never strips tashkeel. Digit normalization is used only for parsing numbers.
- **Enum names:** the spec's source enum is named `DetectionSource` (EMBEDDED_OUTLINE, PRINTED_TOC, HEADING_DETECTION, NONE), to avoid clashing with the existing `com.doova.ktab.enums.book.StructureSource`. On save it maps to PDF_OUTLINE, TEXT_LAYER and HEADINGS. A `NONE` result saves a single section and `StructureStatus.NEEDS_REVIEW`.
- **V1 scope:** PDFs with a text layer. Pages without text (images, scanned pages inside a hybrid book) are kept as `IMAGE_ONLY` pages with empty text, plus an `IMAGE_ONLY_PAGES` warning naming them. A PDF where fewer than 30% of the sampled pages have usable text (a fully scanned book) is rejected with `NO_TEXT_LAYER`: a 422 from `/extract`, and in the job the book fails with "This PDF has no text layer; scanned books are not supported while OCR is off." No OCR fallback exists while OCR is off.
- **No silent acceptance:** suspicious structure produces `warnings`. Any warning with severity `ERROR` sets `StructureStatus.NEEDS_REVIEW`.

## Review Focus

1. **Arabic logical order in extracted lines.** On RTL lines, PDFBox may emit the page number *before* the title (`٥ ........ المقدمة`) or reverse a mixed line. Expected: the TOC parser accepts the number at either end, and the spike (Task 0) checks real-book order. Pinned in Task 3 (`numberAtEitherEndOfTheLine`).
2. **A chapter starting mid-page.** Pages map to exactly one section, so the text at the end of a chapter's last page may belong to the next chapter. Expected: in V1 a page goes to the section whose range starts on or before it (the later one wins on a shared start page), and the audiobook reads whole pages. This is a documented V1 limitation, pinned in Task 5 (`sharedStartPageGoesToTheLaterSection`).
3. **The flag flipping while a job is running.** Expected: the flag is read only when a job is *launched*. A running job finishes on the pipeline it started on, and nothing is re-routed mid-flight. Pinned in Task 1.
4. **Re-running the audiobook after a crash.** Expected: chapters that already have a `BookAudioChapter` row with an existing MP3 are skipped, no audio is paid for twice, and `hasAudio` is set only when every chapter exists. Pinned in Task 7 (`resumeSkipsFinishedChapters`).
5. **TTS chunk boundaries.** A chunk must never split a word or exceed the model's character limit, and the timing offsets must add up with no drift across chunks. Expected: chunks break at paragraph, then sentence, then whitespace, and the offset uses each chunk's measured `ffprobe` duration. Pinned in Task 6 (`neverSplitsAWord`, `offsetsUseMeasuredChunkDurations`).
6. **An encrypted or fully scanned PDF reaching the native job.** With OCR off, scanned books now *do* reach it. Expected: it fails fast with a clear reason (`ENCRYPTED` / `NO_TEXT_LAYER`), not an empty book marked COMPLETED. Pinned in Task 2 and Task 5.
7. **A hybrid book (some scanned pages).** Expected: the text pages are extracted, the scanned pages are kept as empty `IMAGE_ONLY` pages, and an `IMAGE_ONLY_PAGES` warning lists them. Their missing text is visible, not silent. Pinned in Task 2 (`imageOnlyPagesAreKeptEmptyAndReported`) and Task 5.

---

## File Structure

| File | Change | Responsibility |
| --- | --- | --- |
| `features/studio/config/StudioProperties.java` | add `enabled` | the switch |
| `enums/book/IngestionRoute.java` | add `NATIVE` | route value |
| `features/ingestion/routing/IngestionRouter.java` | resolve STUDIO→NATIVE when Studio is off and OCR→NATIVE when OCR is off; launch `nativeIngestionJob` | branch point |
| `features/ingestion/config/IngestionProperties.java` (or new `OcrSwitchProperties`) | `ktab.ocr.enabled` | the OCR switch |
| `event/listener/OcrEventListener.java` | queue path only when OCR is on | closes the queue door to OCR |
| `features/ocr/web/OcrJobController.java` | 409 on 5 launch endpoints while OCR is off | closes the admin door to OCR |
| `features/studio/reconciler/StudioOrphanReconciler.java` | early return when off | no Studio calls when off |
| `features/extraction/dto/*` | **create** records + enums | the spec's result model |
| `features/extraction/pdf/{PdfValidationService,PdfMetadataExtractor,PdfPageExtractor}.java` | **create** | spec §1–3 |
| `features/extraction/text/{ArabicNumberUtils,ArabicTextCleaner,ArabicTextUtils}.java` | **create** | spec §3–4, §8 |
| `features/extraction/structure/*` | **create** 8 classes | spec §5–14 |
| `features/extraction/quality/StructureQualityChecker.java` | **create** | Phase 5 warnings |
| `features/extraction/BookExtractionService.java` | **create** | orchestrates the above |
| `features/extraction/web/BookExtractionController.java` | **create** | `POST /api/books/extract` |
| `features/extraction/persist/ExtractionPersister.java` | **create** | result → shared tables |
| `features/extraction/batch/{NativeIngestionBatchConfig,NativeExtractTasklet}.java` | **create** | `nativeIngestionJob` |
| `features/nativetts/*` | **create** chunker, provider, synthesizer, job | native audiobook |
| `features/audiobook/AudiobookLauncher.java` | **create** | picks Studio or native job by flag |
| `features/studio/web/StudioJobController.java` | use `AudiobookLauncher` | same endpoint for the frontend |
| `src/main/resources/application.properties`, `.env` | new keys | config |
| `src/test/resources/extraction/*.pdf` | **create** fixtures | committed test PDFs |
| `src/test/java/.../extraction/fixtures/ExtractionFixtureGenerator.java` | **create** | regenerates the fixtures (Chromium) |

---

### Task 0: Spike — real books and the standard TTS endpoint

**Files:** create `docs/extraction/spike-2026-10-01.md`.

- [ ] **Step 1: Extraction order on two real Ktab digital books.** Pick one with bookmarks and one with a printed فهرس. Run:

```bash
pdfinfo book.pdf
pdftotext -f 1 -l 40 -layout book.pdf - | head -200      # look at the TOC lines
```

Also run PDFBox's `PDFTextStripper` on the same page (a 10-line `jshell` with the project classpath, or `./mvnw -q exec:java` on a scratch class). Record, for TOC lines, whether the number comes before or after the title, and whether letters come out in logical order.

- [ ] **Step 2: The standard TTS endpoint** (uses a few hundred characters of credit):

```bash
curl -s -X POST "https://api.elevenlabs.io/v1/text-to-speech/F2jHHIMyJTh1pdwoLzNQ/with-timestamps?output_format=mp3_44100_128" \
  -H "xi-api-key: $ELEVENLABS_API_KEY" -H "content-type: application/json" \
  -d '{"text":"الفصل الأول. كان يا ما كان في قديم الزمان.","model_id":"eleven_multilingual_v2","previous_text":"","next_text":""}' \
  | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const j=JSON.parse(s);console.log(Object.keys(j),j.alignment.characters.length,j.alignment.character_end_times_seconds.at(-1))})'
```

Repeat with `"model_id":"eleven_v3"`. Record: whether both return `alignment`; whether `previous_text`/`next_text` are accepted per model; and each model's character limit (send a 6,000-character text and read the error).

- [ ] **Step 3: Write the findings** to `docs/extraction/spike-2026-10-01.md`: TOC line order, the chosen `ktab.native-tts.model-id`, `max-chars-per-request` and `send-context`.
- [ ] **Step 4: Commit** — `git add docs/extraction && git commit -m "docs(extraction): spec and spike findings"`.

---

### Task 1: The switch and the NATIVE route

**Files:** `StudioProperties.java`, `IngestionRoute.java`, `IngestionRouter.java`, `StudioOrphanReconciler.java`, `application.properties`, `.env` (local); test `src/test/java/com/doova/ktab/features/ingestion/routing/IngestionRouterRouteTest.java`.

**Produces:** `StudioProperties#isEnabled()`, `IngestionRoute.NATIVE`, and `@Qualifier("nativeIngestionJob") Job` consumed by the router. The job bean comes in Task 5; until then, a placeholder `Job` bean named `nativeIngestionJob` that fails with "not implemented" keeps the context loading (`NativeIngestionBatchConfig`, replaced in Task 5).

- [ ] **Step 1: Failing tests.** Make `resolveRoute` package-private and static-testable by extracting it into `RouteResolver` (pure):

```java
package com.doova.ktab.features.ingestion.routing;

class IngestionRouterRouteTest {

    private final IngestionProperties.Classification cfg = new IngestionProperties().getClassification();

    @Test
    void digitalGoesToStudioWhenStudioIsEnabled() {
        assertThat(RouteResolver.resolve(PdfType.DIGITAL, cfg, true, false, null, false)).isEqualTo(IngestionRoute.STUDIO);
    }

    @Test
    void digitalGoesToNativeWhenStudioIsDisabled() {
        assertThat(RouteResolver.resolve(PdfType.DIGITAL, cfg, false, false, null, false)).isEqualTo(IngestionRoute.NATIVE);
    }

    @Test
    void withOcrOffEveryNonStudioBookGoesNative() {
        for (PdfType t : List.of(PdfType.SCANNED, PdfType.HYBRID_OCR, PdfType.MIXED, PdfType.UNKNOWN)) {
            assertThat(RouteResolver.resolve(t, cfg, false, false, null, false)).as(t.name()).isEqualTo(IngestionRoute.NATIVE);
            assertThat(RouteResolver.resolve(t, cfg, true, false, null, false)).as(t.name()).isEqualTo(IngestionRoute.NATIVE);
        }
    }

    @Test
    void withOcrOnScannedGoesToOcrAsToday() {
        assertThat(RouteResolver.resolve(PdfType.SCANNED, cfg, false, true, null, false)).isEqualTo(IngestionRoute.OCR);
    }

    @Test
    void aBookLockedToOcrRunsNativeWhileOcrIsOff() {
        assertThat(RouteResolver.resolve(PdfType.SCANNED, cfg, false, false, IngestionRoute.OCR, true))
                .isEqualTo(IngestionRoute.NATIVE);
    }

    @Test
    void aBookLockedToStudioRunsNativeWhileStudioIsDisabled() {
        assertThat(RouteResolver.resolve(PdfType.DIGITAL, cfg, false, false, IngestionRoute.STUDIO, true))
                .isEqualTo(IngestionRoute.NATIVE);
    }

    @Test
    void shadowModeMeansOcrOnlyWhileOcrIsOn() {
        cfg.setShadowMode(true);
        assertThat(RouteResolver.resolve(PdfType.DIGITAL, cfg, false, true, null, false)).isEqualTo(IngestionRoute.OCR);
        assertThat(RouteResolver.resolve(PdfType.DIGITAL, cfg, false, false, null, false)).isEqualTo(IngestionRoute.NATIVE);
    }

    @Test
    void ocrJobIsNeverLaunchedWhileOcrIsOff() {
        // IngestionRouter with mocks: ingest(...) on a SCANNED book launches nativeIngestionJob, never ocrJob
        router.ingest(7L, "k");
        verify(jobLauncher).run(eq(nativeIngestionJob), any());
        verify(jobLauncher, never()).run(eq(ocrJob), any());
    }
}
```

- [ ] **Step 2:** `./mvnw -q test -Dtest=IngestionRouterRouteTest` → FAIL (no `RouteResolver`, no `NATIVE`).
- [ ] **Step 3: Implement.**

```java
// IngestionRoute: add after OCR
/** Ktab's own text-layer extraction (features.extraction); used for DIGITAL books while Studio is disabled. */
NATIVE
```

```java
package com.doova.ktab.features.ingestion.routing;

/** Pure routing decision, so the Studio switch is unit-testable. */
final class RouteResolver {
    private RouteResolver() { }

    static IngestionRoute resolve(PdfType type, IngestionProperties.Classification cfg, boolean studioEnabled,
                                  boolean ocrEnabled, IngestionRoute lockedRoute, boolean locked) {
        IngestionRoute route;
        if (locked && lockedRoute != null) {
            route = lockedRoute;
        } else if (!cfg.isEnabled() || cfg.isShadowMode()) {
            route = IngestionRoute.OCR;
        } else {
            route = cfg.getRouting().getOrDefault(type, IngestionRoute.OCR);
        }
        // The Studio switch: without Studio access, Studio's books go to Ktab's own extraction.
        if (route == IngestionRoute.STUDIO && !studioEnabled) {
            return IngestionRoute.NATIVE;
        }
        // The OCR switch: OCR is off (owner, 2026-10-01), so OCR's books go to Ktab's own extraction too.
        if (route == IngestionRoute.OCR && !ocrEnabled) {
            return IngestionRoute.NATIVE;
        }
        return route;
    }
}
```

In `IngestionRouter`:
- Inject `StudioProperties studioProperties` and `@Qualifier("nativeIngestionJob") Job nativeIngestionJob`.
- `resolveRoute(book, result)` returns `RouteResolver.resolve(result.getPdfType(), cfg, studioProperties.isEnabled(), properties.isOcrEnabled(), book.getIngestionRoute(), book.isIngestionRouteLocked())`.
- `launch` gets `case NATIVE -> launchNative(bookId, pdfKey);`, a copy of `launchStudio` with `nativeIngestionJob`.
- `findRunning` also checks `nativeIngestionJob` executions.

In `StudioProperties` add:

```java
/** The Studio switch (KTAB_STUDIO_ENABLED). false = Ktab's own extraction + TTS. Read only when a job is launched. */
private boolean enabled = false;
```

At the top of `StudioOrphanReconciler`'s scheduled method: `if (!props.isEnabled()) { return; }`.

In `IngestionProperties` (top level, next to `classification`) add:

```java
/** The OCR switch (KTAB_OCR_ENABLED). false = ocrJob is never launched; its books go to nativeIngestionJob. */
@Value("${ktab.ocr.enabled:false}") // or a plain field bound from ktab.ingestion.ocr-enabled; keep one name: ktab.ocr.enabled
private boolean ocrEnabled = false;
```

Bind it as `ktab.ocr.enabled` (a small `@ConfigurationProperties(prefix = "ktab.ocr")` class `OcrSwitchProperties` with one field `enabled` is the clean way, injected into the router instead of a field on `IngestionProperties`; pick that if `@Value` on a `@ConfigurationProperties` class reads oddly).

Also: the classification step stays on even with OCR off. It still records `pdfType` for reporting and decides Studio vs native when Studio is on.

In `application.properties`, add `ktab.studio.enabled=${KTAB_STUDIO_ENABLED:false}` and `ktab.ocr.enabled=${KTAB_OCR_ENABLED:false}`. In `.env`, add `KTAB_STUDIO_ENABLED=false` and `KTAB_OCR_ENABLED=false`.

Close the two other doors to OCR (both verified in the code on 2026-10-01):

1. **`OcrEventListener` queue path (lines 52–58):** when `qstash.ocr.worker-enabled` / `aws.sqs.ocr.worker-enabled` is on, it bypasses routing and calls `queueService.publishBookPages(...)`, sending every page straight to OCR. Change the condition to `queueWorkerEnabled && ocrEnabled && queueService != null && queueService.isEnabled()`. With OCR off, the book always goes through `ingestionRouter.ingest(...)`. Test: `OcrEventListenerTest#queuePathIsSkippedWhileOcrIsOff` verifies `publishBookPages` is never called and `ingest` is called once.
2. **`OcrJobController` (`/api/ocr/...`):** it launches `ocrJob` directly in `/books/{id}/start`, `/pages/retry-flagged` and `/resume-latest`. It launches `restructureJob` in `/restructure` and `harmonizeJob` in `/harmonize`, and those two also call Gemini. While OCR is off, each of these five endpoints returns **409** `{"message":"OCR is disabled (KTAB_OCR_ENABLED=false)"}` before launching anything. Test: `OcrJobControllerOcrSwitchTest` checks all five return 409 and `jobLauncher` is never called. `/rotate` and `/split` only edit stored pages, so they stay available.

Add `NativeIngestionBatchConfig` with a placeholder job bean (replaced in Task 5):

```java
@Bean
public Job nativeIngestionJob(JobRepository repo, PlatformTransactionManager tx) {
    return new JobBuilder("nativeIngestionJob", repo).start(new StepBuilder("nativeExtractStep", repo)
            .tasklet((c, ctx) -> { throw new IllegalStateException("nativeIngestionJob not implemented yet"); }, tx)
            .build()).build();
}
```

- [ ] **Step 4:** `./mvnw -q test -Dtest='IngestionRouterRouteTest,com.doova.ktab.features.ingestion.**'` → PASS. Then `./mvnw -q test` to confirm the context still loads (the existing failures in the OCR live tests and `MetadataServiceImplTest` are known, and unrelated).
- [ ] **Step 5: Commit** — `feat(ingestion): KTAB_STUDIO_ENABLED and KTAB_OCR_ENABLED switches, NATIVE route`.

---

### Task 2: Extraction core — validation, metadata, pages, cleaning (spec §1–4, §8)

**Files (create, base `com.doova.ktab.features.extraction`):** `dto/{BookMetadata,PageContent,ExtractionWarning,WarningCode,Severity}.java`, `pdf/{PdfValidationService,PdfMetadataExtractor,PdfPageExtractor,PdfRejectedException}.java`, `text/{ArabicNumberUtils,ArabicTextCleaner}.java`; tests under `src/test/java/.../extraction/`; fixtures `src/test/resources/extraction/`.

**Produces:**

```java
public record BookMetadata(String title, String author, String subject, String keywords, String language, int pageCount) { }
public record PageContent(int pdfPage, String rawText, String cleanedText, String printedPageLabel,
                          String runningHeader, List<String> lines, boolean imageOnly) { }
public enum WarningCode { TOC_PAGE_MISMATCH, MISSING_CHAPTERS, DUPLICATE_HEADING, INVALID_RANGE, TOC_PAGE_OUT_OF_RANGE,
    EMPTY_CHAPTER, SHORT_CHAPTER, LARGE_GAP, LOW_ARABIC_RATIO, EXTRACTION_ARTIFACTS, NO_STRUCTURE, IMAGE_ONLY_PAGES }
public enum Severity { INFO, WARNING, ERROR }
public record ExtractionWarning(WarningCode code, Severity severity, String message) { }
public class PdfRejectedException extends RuntimeException {  // -> 422
    public enum Reason { EMPTY, NOT_PDF, TOO_LARGE, CORRUPTED, ENCRYPTED, NO_TEXT_LAYER }
    private final Reason reason; /* ctor(Reason, String), getter */ }
// PdfValidationService.load(byte[] pdf) -> PDDocument (throws PdfRejectedException)
// PdfMetadataExtractor.extract(PDDocument) -> BookMetadata
// PdfPageExtractor.extract(PDDocument) -> List<PageContent>   (raw only; cleanedText filled by ArabicTextCleaner)
// ArabicTextCleaner.clean(List<PageContent>, BookMetadata) -> List<PageContent>
// ArabicNumberUtils.toAsciiDigits(String) / parsePageNumber(String) -> OptionalInt
```

- [ ] **Step 0: Fixtures.** Create `src/test/java/com/doova/ktab/features/extraction/fixtures/ExtractionFixtureGenerator.java`, a `main` method run once, with its output committed. It renders RTL HTML to PDF with Playwright and Chromium (which shapes Arabic and embeds ToUnicode maps, so the text layer is real), then post-processes with PDFBox. It writes:
  - `book-outline.pdf`: 14 pages. Pages 1–3 are front matter (cover, copyright, dedication). The body has المقدمة (p4), الفصل الأول (p6) with المبحث الأول (p7) and المبحث الثاني (p8), الفصل الثاني (p10) and الخاتمة (p13). Every body page has the running header "اسم الكتاب" on line 1 and an Arabic-Indic page number footer (printed page = PDF page − 3). A PDFBox outline is added with those 6 entries and their levels.
  - `book-printed-toc.pdf`: the same body, no outline. PDF page 3 is a فهرس المحتويات with dot leaders and Arabic-Indic printed numbers.
  - `book-headings.pdf`: the same body, no outline and no TOC.
  - `encrypted.pdf`: `book-headings.pdf` saved with `StandardProtectionPolicy` and a user password.
  - `image-only.pdf`: 3 pages, each an embedded PNG and no text.
  - `book-hybrid.pdf`: `book-headings.pdf` with PDF pages 7 and 11 replaced by image-only pages (a scanned page inside a digital book).
  - `not-a-pdf.pdf`: the bytes `hello`.

  The page HTML uses `<div class="page" style="page-break-after:always">`, the header as the first `<p>`, and the footer number as the last `<p>`. The generator is documented in the class Javadoc with the command `./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=...ExtractionFixtureGenerator`.

- [ ] **Step 1: Failing tests.**

```java
class ArabicNumberUtilsTest {
    @Test void convertsAllThreeDigitFamilies() {
        assertThat(ArabicNumberUtils.toAsciiDigits("١٢٣ ۱۲۳ 123")).isEqualTo("123 123 123");
    }
    @Test void parsesAStandalonePageNumber() {
        assertThat(ArabicNumberUtils.parsePageNumber(" ٤٢ ")).hasValue(42);
        assertThat(ArabicNumberUtils.parsePageNumber("- ٧ -")).hasValue(7);
        assertThat(ArabicNumberUtils.parsePageNumber("الفصل ٢")).isEmpty();
    }
}

class PdfValidationServiceTest {
    private final PdfValidationService v = new PdfValidationService(50L * 1024 * 1024);
    @Test void rejectsEmptyNonPdfEncryptedAndImageOnly() {
        assertThatThrownBy(() -> v.load(new byte[0])).hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.EMPTY);
        assertThatThrownBy(() -> v.load(fixture("not-a-pdf.pdf"))).hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.NOT_PDF);
        assertThatThrownBy(() -> v.load(fixture("encrypted.pdf"))).hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.ENCRYPTED);
        assertThatThrownBy(() -> v.load(fixture("image-only.pdf"))).hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.NO_TEXT_LAYER);
    }
    @Test void rejectsTooLarge() {
        assertThatThrownBy(() -> new PdfValidationService(10).load(fixture("book-headings.pdf")))
                .hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.TOO_LARGE);
    }
    @Test void acceptsADigitalArabicPdf() throws Exception {
        try (PDDocument doc = v.load(fixture("book-headings.pdf"))) { assertThat(doc.getNumberOfPages()).isEqualTo(14); }
    }
}

class PdfPageExtractorAndCleanerTest {
    @Test void extractsEveryPageWithItsPdfNumberAndKeepsRawText() throws Exception {
        List<PageContent> pages = extract("book-headings.pdf");
        assertThat(pages).hasSize(14).extracting(PageContent::pdfPage).startsWith(1, 2, 3).endsWith(14);
        assertThat(pages.get(5).rawText()).contains("الفصل الأول").contains("اسم الكتاب"); // header still in raw
    }
    @Test void imageOnlyPagesAreKeptEmptyAndReported() throws Exception {
        List<PageContent> pages = clean(extract("book-hybrid.pdf"));
        assertThat(pages).hasSize(14);
        assertThat(pages.get(6).cleanedText()).isEmpty();
        assertThat(pages.get(6).imageOnly()).isTrue();
        assertThat(pages.get(5).imageOnly()).isFalse();
    }
    @Test void cleaningRemovesRepeatedHeaderAndPageNumberButNeverTouchesLettersOrTashkeel() throws Exception {
        List<PageContent> pages = clean(extract("book-headings.pdf"));
        PageContent p6 = pages.get(5);
        assertThat(p6.cleanedText()).doesNotContain("اسم الكتاب").contains("الفصل الأول");
        assertThat(p6.runningHeader()).isEqualTo("اسم الكتاب");
        assertThat(p6.printedPageLabel()).isEqualTo("٣");
        assertThat(p6.cleanedText().lines()).noneMatch(l -> l.strip().equals("٣"));
        String withTashkeel = "كَتَبَ الطَّالِبُ";
        assertThat(ArabicTextCleaner.collapseWhitespace("  " + withTashkeel + "   \n\n\n\n")).isEqualTo(withTashkeel);
    }
}
```

`fixture(name)`, `extract(name)` and `clean(...)` are small test helpers that read from `src/test/resources/extraction/`.

- [ ] **Step 2:** `./mvnw -q test -Dtest='ArabicNumberUtilsTest,PdfValidationServiceTest,PdfPageExtractorAndCleanerTest'` → compile FAIL.
- [ ] **Step 3: Implement.**
  - **`ArabicNumberUtils`:** `toAsciiDigits` delegates to the existing `ArabicTextNormalizer.convertDigitsToAscii` (which already covers both ٠–٩ and ۰–۹). `parsePageNumber` strips `-–—()[]` and spaces, then matches `^\d{1,4}$`.
  - **`PdfValidationService.load`:**
    - empty → `EMPTY`; over the size limit → `TOO_LARGE`; not starting with `%PDF-` → `NOT_PDF`;
    - `Loader.loadPDF(bytes)` throwing `InvalidPasswordException` → `ENCRYPTED`, any other `IOException` → `CORRUPTED`;
    - `doc.isEncrypted()` → `ENCRYPTED`;
    - then text-layer sampling: up to 20 pages spread through the book with `PDFTextStripper`. If fewer than 30% of the sampled pages have more than 50 non-space characters → `NO_TEXT_LAYER`. Reuse `TextLayerQualityAssessor.assess(text, "ar", …)` to reject garbled layers, the same check `PdfTypeClassifier` uses.
    - The size limit comes from `ktab.extraction.max-upload-bytes`, default 200 MB.
  - **`PdfMetadataExtractor`:** reads `PDDocumentInformation` (title, author, subject, keywords) and the catalog's `/Lang`. Blank strings become `null`.
  - **`PdfPageExtractor`:** for each page, a fresh `PDFTextStripper` with `setStartPage(i)`, `setEndPage(i)`, `setSortByPosition(true)` and `setShouldSeparateByBeads(true)`. Fill `rawText` and `lines`. `imageOnly` is true when the page has 50 or fewer non-space characters and at least one image XObject in its resources. The other fields stay `null` until the cleaner runs. The quality checker (Task 4) adds one `IMAGE_ONLY_PAGES` WARNING listing those page numbers, raised to ERROR when they exceed 20% of the book.
  - **`ArabicTextCleaner.clean(pages, metadata)`:**
    1. **Running header and footer:** a line that occurs, normalized (whitespace collapsed, digits replaced by `#`), within the first 2 or last 2 non-empty lines of at least 40% of the pages (minimum 3 pages) is chrome. Remove it from `cleanedText` and record the first such line as `runningHeader`.
    2. **Standalone page numbers:** if one of the first or last 2 lines `parsePageNumber`s, it becomes `printedPageLabel` (keeping its original digits) and is removed from the cleaned text.
    3. **Title and author lines:** remove lines exactly equal to the metadata title or author when they repeat on at least 40% of pages.
    4. **Artifacts:** remove lines made only of `�`, control characters or repeated punctuation.
    5. **Whitespace:** collapse runs of spaces/tabs to one space, and three or more newlines to two. Never change any other character.
- [ ] **Step 4:** run the tests again → PASS.
- [ ] **Step 5: Commit** — `feat(extraction): PDF validation, metadata, page extraction and Arabic cleaning`.

---

### Task 3: Structure — outline, printed TOC, page offset, headings (spec §5–11)

**Files (create, `features/extraction/structure/`):** `RawEntry.java`, `EmbeddedOutlineExtractor.java`, `PrintedTocDetector.java`, `PrintedTocParser.java`, `PageNumberResolver.java`, `HeadingDetector.java`; tests in the same package.

**Produces:**

```java
/** One structure entry before normalization. printedPage is set only by the printed TOC path. */
public record RawEntry(String title, int level, Integer startPage, Integer printedPage) { }
// EmbeddedOutlineExtractor.extract(PDDocument) -> List<RawEntry>          (startPage resolved, may be empty)
// PrintedTocDetector.findTocPages(List<PageContent>) -> List<Integer>     (pdf pages, within first 40)
// PrintedTocParser.parse(List<PageContent> tocPages) -> List<RawEntry>    (printedPage set, startPage null)
// PageNumberResolver.resolve(List<RawEntry>, List<PageContent>) -> Resolution(List<RawEntry> entries, int offset, double confidence, List<ExtractionWarning>)
// HeadingDetector.detect(List<PageContent>) -> List<RawEntry>
```

- [ ] **Step 1: Failing tests.**

```java
class EmbeddedOutlineExtractorTest {
    @Test void readsTitlesLevelsAndDestinationPages() throws Exception {
        List<RawEntry> e = new EmbeddedOutlineExtractor().extract(load("book-outline.pdf"));
        assertThat(e).extracting(RawEntry::title).containsExactly(
                "المقدمة", "الفصل الأول", "المبحث الأول", "المبحث الثاني", "الفصل الثاني", "الخاتمة");
        assertThat(e).extracting(RawEntry::level).containsExactly(1, 1, 2, 2, 1, 1);
        assertThat(e).extracting(RawEntry::startPage).containsExactly(4, 6, 7, 8, 10, 13);
    }
    @Test void aPdfWithoutOutlineGivesNothing() throws Exception {
        assertThat(new EmbeddedOutlineExtractor().extract(load("book-headings.pdf"))).isEmpty();
    }
}

class PrintedTocParserTest {
    @Test void numberAtEitherEndOfTheLine() {
        assertThat(PrintedTocParser.parseLine("المقدمة ...................... ٥")).contains(new RawEntry("المقدمة", 1, null, 5));
        assertThat(PrintedTocParser.parseLine("١٢ .................. الفصل الأول")).contains(new RawEntry("الفصل الأول", 1, null, 12));
        assertThat(PrintedTocParser.parseLine("المبحث الأول ۱۷")).contains(new RawEntry("المبحث الأول", 2, null, 17));
        assertThat(PrintedTocParser.parseLine("هذا سطر عادي بلا رقم")).isEmpty();
    }
    @Test void findsAndParsesTheFixtureToc() throws Exception {
        List<PageContent> pages = cleanedPages("book-printed-toc.pdf");
        List<Integer> tocPages = new PrintedTocDetector().findTocPages(pages);
        assertThat(tocPages).containsExactly(3);
        assertThat(new PrintedTocParser().parse(pages.subList(2, 3))).extracting(RawEntry::printedPage)
                .containsExactly(1, 3, 4, 5, 7, 10);
    }
}

class PageNumberResolverTest {
    @Test void computesTheOffsetFromTitlesFoundInTheBodyAndValidatesIt() throws Exception {
        List<PageContent> pages = cleanedPages("book-printed-toc.pdf");
        List<RawEntry> toc = new PrintedTocParser().parse(pages.subList(2, 3));
        PageNumberResolver.Resolution r = new PageNumberResolver().resolve(toc, pages);
        assertThat(r.offset()).isEqualTo(3);
        assertThat(r.entries()).extracting(RawEntry::startPage).containsExactly(4, 6, 7, 8, 10, 13);
        assertThat(r.confidence()).isGreaterThanOrEqualTo(0.8);
        assertThat(r.warnings()).isEmpty();
    }
    @Test void anEntryWhoseTitleIsNotNearItsComputedPageGetsAWarning() {
        // a TOC entry pointing past the end of a 14-page book
        PageNumberResolver.Resolution r = new PageNumberResolver().resolve(
                List.of(new RawEntry("الفصل الأول", 1, null, 3), new RawEntry("ملحق", 1, null, 90)), bodyPages());
        assertThat(r.warnings()).extracting(ExtractionWarning::code).contains(WarningCode.TOC_PAGE_OUT_OF_RANGE);
    }
}

class HeadingDetectorTest {
    @Test void findsChapterAndSpecialHeadingsButNotMentionsInsideParagraphs() throws Exception {
        List<RawEntry> h = new HeadingDetector().detect(cleanedPages("book-headings.pdf"));
        assertThat(h).extracting(RawEntry::title).containsExactly(
                "المقدمة", "الفصل الأول", "المبحث الأول", "المبحث الثاني", "الفصل الثاني", "الخاتمة");
        assertThat(h).extracting(RawEntry::startPage).containsExactly(4, 6, 7, 8, 10, 13);
        assertThat(h).extracting(RawEntry::level).containsExactly(1, 1, 2, 2, 1, 1);
    }
}
```

The fixture body includes the sentence "كما ذكرنا في الفصل الأول من هذا الكتاب" inside a long paragraph on page 11. The heading detector must not pick it up.

- [ ] **Step 2:** run → compile FAIL.
- [ ] **Step 3: Implement.**
  - **`EmbeddedOutlineExtractor`:** depth-first walk of `doc.getDocumentCatalog().getDocumentOutline()`, with level = depth.
    - **Page resolution:** use `item.getDestination()`, or else `((PDActionGoTo) item.getAction()).getDestination()`. A `PDNamedDestination` is looked up with `doc.getDocumentCatalog().findNamedDestinationPage(…)`. A `PDPageDestination` gives `retrievePageNumber() + 1`, or `doc.getPages().indexOf(dest.getPage()) + 1`.
    - **Unusable items:** skip items with no resolvable page, and items whose page is outside `1..n`.
    - **Useless outline:** when the outline has fewer than 2 usable entries, or one entry pointing at page 1 only, return empty, so the next strategy runs.
  - **`PrintedTocDetector`:** among PDF pages 1..min(40, n), a page is a TOC page when its first 5 non-empty cleaned lines contain one of `الفهرس`, `فهرس المحتويات`, `المحتويات`, `المحتويات العامة` (after `ArabicTextNormalizer.normalize`), **and** at least 3 lines parse as TOC lines. Following pages are included while they keep at least 3 TOC lines.
  - **`PrintedTocParser.parseLine(line)`:**
    1. Normalize digits only for the match.
    2. Pattern A `^(.+?)[\s.…·_\-]{2,}(\d{1,4})\s*$`, then pattern B `^(\d{1,4})[\s.…·_\-]{2,}(.+?)$`, then pattern C `^(.+?)\s+(\d{1,4})$`, which requires a known heading word or at least 2 dot leaders.
    3. Strip leaders from the title.
    4. **Level:** 1 for الفصل/الباب/الجزء/القسم/المقدمة/تمهيد/الخاتمة/مدخل/الملاحق; 2 for المبحث; 3 for المطلب/الفرع. Otherwise use the line's leading indentation in `PageContent.lines`, where more leading spaces means a deeper level, 2 at most.
  - **`PageNumberResolver`:** for each entry, search the PDF pages for its normalized title as a heading line (a line equal to, or starting with, the title within the first 8 lines of the page).
    - Each match gives a candidate offset `pdfPage − printedPage`. The book offset is the most frequent candidate (ties → smallest).
    - Then `startPage = printedPage + offset`. An entry whose title is not found within ±2 pages of its computed page gets a `TOC_PAGE_MISMATCH` warning. One whose computed page is outside `1..n` gets `TOC_PAGE_OUT_OF_RANGE` and is dropped.
    - `confidence = matched / entries`. With no anchor at all, fall back to the median offset between printed page labels and PDF pages (from `PageContent.printedPageLabel`) at confidence 0.6. If that is also absent, use offset 0 at confidence 0.3 with a `TOC_PAGE_MISMATCH` warning.
  - **`HeadingDetector`:** candidates are lines matching `^(الفصل|الباب|القسم|الجزء|المبحث|المطلب)\s+\S+` (at most 8 words) or exactly `المقدمة|مقدمة المؤلف|تمهيد|الخاتمة|خاتمة`, after digit and whitespace normalization. A candidate is kept only when **all** of these hold:
    1. It is in the first 6 non-empty lines of its page.
    2. The line is short (at most 60 characters).
    3. It isn't inside a paragraph: the previous line is empty, or it's the first line.
    4. At least 200 characters of text follow on the page or the next page.
    5. When PDFBox font sizes are available (`TextPosition.getFontSizeInPt`, collected by a small `PDFTextStripper` subclass for that page), its average size is at least the page's median size.

    Level is 1 for الفصل/الباب/القسم/الجزء and special headings, 2 for المبحث, 3 for المطلب. A heading repeated on consecutive pages (a running header) is kept only once, at its first page.
- [ ] **Step 4:** run → PASS.
- [ ] **Step 5: Commit** — `feat(extraction): outline, printed Arabic TOC, page offset and heading detection`.

---

### Task 4: Normalize, chapters, quality, the service and the endpoint (spec §12–18, Phase 5)

**Files (create):** `dto/{TocEntry,TocEntryType,DetectionSource,StructureDetection,Section,Chapter,BookExtractionResult}.java`, `structure/{TocNormalizer,ChapterBuilder,BookStructureExtractor}.java`, `quality/StructureQualityChecker.java`, `BookExtractionService.java`, `web/BookExtractionController.java`, `config/ExtractionProperties.java`; tests.

**Produces:**

```java
public enum TocEntryType { INTRODUCTION, CHAPTER, SECTION, SUBSECTION, CONCLUSION, OTHER }
public enum DetectionSource { EMBEDDED_OUTLINE, PRINTED_TOC, HEADING_DETECTION, NONE }
public record StructureDetection(DetectionSource source, double confidence) { }
public record TocEntry(String title, int level, int startPage, int endPage, TocEntryType type, List<TocEntry> children) { }
public record Section(String title, int level, int startPage, int endPage, TocEntryType type, List<Section> subsections) { }
public record Chapter(int index, String title, int startPage, int endPage, TocEntryType type, String text,
                      List<PageContent> pages, List<Section> sections) { }
public record BookExtractionResult(BookMetadata metadata, StructureDetection structureDetection, List<TocEntry> toc,
                                   List<Chapter> chapters, List<PageContent> pages, List<ExtractionWarning> warnings) { }
// BookExtractionService.extract(byte[] pdf) -> BookExtractionResult
```

- [ ] **Step 1: Failing tests.**

```java
class BookExtractionServiceTest {
    private final BookExtractionService service = BookExtractionServiceFactory.forTests(); // wires the real classes, no Spring

    @Test void outlineIsPreferredAndGivesHighConfidence() {
        BookExtractionResult r = service.extract(fixture("book-outline.pdf"));
        assertThat(r.structureDetection()).isEqualTo(new StructureDetection(DetectionSource.EMBEDDED_OUTLINE, 1.0));
    }

    @Test void printedTocIsUsedWhenThereIsNoOutline() {
        BookExtractionResult r = service.extract(fixture("book-printed-toc.pdf"));
        assertThat(r.structureDetection().source()).isEqualTo(DetectionSource.PRINTED_TOC);
        assertThat(r.structureDetection().confidence()).isGreaterThanOrEqualTo(0.8);
    }

    @Test void headingsAreTheLastResort() {
        BookExtractionResult r = service.extract(fixture("book-headings.pdf"));
        assertThat(r.structureDetection().source()).isEqualTo(DetectionSource.HEADING_DETECTION);
        assertThat(r.structureDetection().confidence()).isBetween(0.5, 0.8);
    }

    @Test void hierarchyAndRangesAreBuiltTheSameWayForEverySource() {
        for (String f : List.of("book-outline.pdf", "book-printed-toc.pdf", "book-headings.pdf")) {
            BookExtractionResult r = service.extract(fixture(f));
            assertThat(r.toc()).extracting(TocEntry::title).as(f).containsExactly("المقدمة", "الفصل الأول", "الفصل الثاني", "الخاتمة");
            assertThat(r.toc().get(1).children()).extracting(TocEntry::title).containsExactly("المبحث الأول", "المبحث الثاني");
            assertThat(r.chapters()).extracting(Chapter::startPage, Chapter::endPage)
                    .containsExactly(tuple(4, 5), tuple(6, 9), tuple(10, 12), tuple(13, 14));
            assertThat(r.chapters()).extracting(Chapter::type).containsExactly(
                    TocEntryType.INTRODUCTION, TocEntryType.CHAPTER, TocEntryType.CHAPTER, TocEntryType.CONCLUSION);
            assertThat(r.chapters().get(1).sections()).extracting(Section::startPage, Section::endPage)
                    .containsExactly(tuple(7, 7), tuple(8, 9));
            assertThat(r.chapters().get(1).text()).contains("الفصل الأول").doesNotContain("اسم الكتاب");
            assertThat(r.chapters().get(1).pages()).extracting(PageContent::pdfPage).containsExactly(6, 7, 8, 9);
        }
    }

    @Test void pagesBeforeTheFirstEntryAreNotLostButAreNotAChapter() {
        BookExtractionResult r = service.extract(fixture("book-outline.pdf"));
        assertThat(r.pages()).hasSize(14);
        assertThat(r.chapters().get(0).startPage()).isEqualTo(4);
    }
}

class StructureQualityCheckerTest {
    @Test void flagsDuplicatesInvalidRangesEmptyAndShortChaptersAndLargeGaps() {
        List<Chapter> chapters = List.of(
                chapter(1, "الفصل الأول", 5, 4, "نص"),             // invalid range
                chapter(2, "الفصل الأول", 6, 6, ""),                // duplicate + empty
                chapter(3, "الفصل الثالث", 7, 7, "قصير"),           // short
                chapter(4, "الفصل الرابع", 8, 300, "نص طويل".repeat(500)));
        List<ExtractionWarning> w = new StructureQualityChecker(new ExtractionProperties()).check(chapters, 300, 0.9);
        assertThat(w).extracting(ExtractionWarning::code).contains(WarningCode.INVALID_RANGE, WarningCode.DUPLICATE_HEADING,
                WarningCode.EMPTY_CHAPTER, WarningCode.SHORT_CHAPTER, WarningCode.LARGE_GAP);
    }
    @Test void lowArabicRatioIsAnError() {
        assertThat(new StructureQualityChecker(new ExtractionProperties()).check(List.of(), 10, 0.2))
                .anySatisfy(x -> assertThat(x).extracting(ExtractionWarning::code, ExtractionWarning::severity)
                        .containsExactly(WarningCode.LOW_ARABIC_RATIO, Severity.ERROR));
    }
}

@WebMvcTest(BookExtractionController.class)
class BookExtractionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BookExtractionService service;

    @Test @WithMockUser(authorities = "ADMIN")
    void encryptedPdfIs422WithItsReason() throws Exception {
        when(service.extract(any())).thenThrow(new PdfRejectedException(PdfRejectedException.Reason.ENCRYPTED, "encrypted"));
        mvc.perform(multipart("/api/books/extract").file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1})).with(csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.reason").value("ENCRYPTED"));
    }
    @Test @WithMockUser(authorities = "READER")
    void onlyAdminsAndLibrariansMayCallIt() throws Exception {
        mvc.perform(multipart("/api/books/extract").file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1})).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
```

Use the project's existing web-slice test setup. If the security config needs extra beans in `@WebMvcTest`, copy the imports from an existing `*ControllerTest` in the repo.

- [ ] **Step 2:** run → compile FAIL.
- [ ] **Step 3: Implement.**
  - **`BookStructureExtractor.extract(doc, pages)`:**
    1. Outline: if it has at least 2 entries → `EMBEDDED_OUTLINE`, confidence 1.0.
    2. Else the printed TOC: detect, parse, resolve. If there are at least 2 entries and confidence ≥ 0.5 → `PRINTED_TOC` with the resolver's confidence (≥ 0.8 = high, else medium).
    3. Else headings: if there are at least 2 → `HEADING_DETECTION`. Confidence is 0.7 when at least 2 distinct level-1 ordinals are found (الأول, الثاني …), else 0.55.
    4. Else `NONE`, confidence 0, with a `NO_STRUCTURE` warning of severity ERROR.

    Each step that runs and fails adds an `INFO` warning saying why it was skipped.
  - **`TocNormalizer.normalize(List<RawEntry>, pageCount)`:**
    1. Sort by `(startPage, original order)` and drop exact duplicates (same title and page).
    2. Clamp levels so no entry is more than 1 deeper than the previous one.
    3. Build the tree with a level stack.
    4. **Type:** map `SectionClassifier.classify(title)`: INTRODUCTION/PREFACE/FOREWORD → INTRODUCTION; CONCLUSION → CONCLUSION; PART/CHAPTER → CHAPTER. Anything else is CHAPTER at level 1, SECTION at level 2, SUBSECTION at level 3+, and OTHER only for FRONT_MATTER/INDEX/BIBLIOGRAPHY/APPENDIX/GLOSSARY.
    5. **End pages:** each entry ends one page before the next entry at the **same or a shallower level**. The last one ends at the parent's end (the book's last page for level 1). When two entries share a start page, the earlier one's end is the same page, never earlier than its start.
  - **`ChapterBuilder.build(tocTree, pages)`:** each level-1 entry becomes a `Chapter`. `index` is 1-based; `pages` covers `startPage..endPage`; `text` joins those pages' `cleanedText` with `\n\n`; `sections` are the children, converted recursively.
  - **`StructureQualityChecker.check(chapters, pageCount, arabicRatio)`:**
    - `INVALID_RANGE` (start > end or outside 1..n) = ERROR;
    - `DUPLICATE_HEADING` (same normalized title at the same level) = WARNING;
    - `EMPTY_CHAPTER` (text blank) = WARNING;
    - `SHORT_CHAPTER` (under `short-chapter-chars`, default 300) = WARNING;
    - `LARGE_GAP` (a chapter longer than `large-gap-ratio`, default 0.6, of the book when there are 3+ chapters) = WARNING;
    - `LOW_ARABIC_RATIO` (arabicRatio below `min-arabic-ratio`, default 0.5) = ERROR;
    - `EXTRACTION_ARTIFACTS` (more than 1% `�`) = WARNING;
    - `MISSING_CHAPTERS` (ordinal sequence skips, e.g. الأول then الثالث, using the existing ordinal words in `SectionClassifier`/`ArabicTextNormalizer`) = WARNING.

    `arabicRatio` = Arabic letters / all letters over every page's cleaned text, computed in the service.
  - **`BookExtractionService.extract(bytes)`:** validate, then metadata, pages, clean, structure, normalize, chapters, quality, and return the result. Close the `PDDocument` in try-with-resources.
  - **`BookExtractionController`:** `POST /api/books/extract`, `consumes = multipart/form-data`, guarded by `@PreAuthorize("hasAnyAuthority('ADMIN','LIBRARIAN','ADMIN_LIBRARIAN')")`. It returns `BookExtractionResult`. An `@ExceptionHandler(PdfRejectedException.class)` maps to 422 `{"reason": "...", "message": "..."}`. Add `pages` to the response only when `?includePages=true` (default false), to keep responses small.
  - **`ExtractionProperties`** (`ktab.extraction.*`): `maxUploadBytes`, `tocSearchPages = 40`, `shortChapterChars = 300`, `largeGapRatio = 0.6`, `minArabicRatio = 0.5`. Raise `spring.servlet.multipart.max-file-size` / `max-request-size` to at least `max-upload-bytes`, but only if they're currently lower (check `application.properties`).
- [ ] **Step 4:** run → PASS.
- [ ] **Step 5: Commit** — `feat(extraction): normalized TOC tree, chapters, quality warnings and POST /api/books/extract`.

---

### Task 5: `nativeIngestionJob` — persist into the shared tables

**Files (create):** `features/extraction/persist/ExtractionPersister.java`, `features/extraction/batch/NativeExtractTasklet.java`. Replace the placeholder in `NativeIngestionBatchConfig.java`. Tests: `ExtractionPersisterTest`, `NativeExtractTaskletTest`.

**Consumes:** `BookExtractionService.extract(byte[])`, plus `BookRepository`, `BookPageRepository`, `BookSectionRepository` and `ObjectStorageService.getBytes(key)` (all existing).

- [ ] **Step 1: Failing tests** (Mockito, with argument captors on `saveAll`):

```java
class ExtractionPersisterTest {
    @Test void writesOnePageRowPerPdfPageWithRawAndCleanText() {
        persister.persist(book, resultFromFixture("book-outline.pdf"));
        List<BookPage> pages = capturedPages();
        assertThat(pages).hasSize(14);
        BookPage p6 = pages.get(5);
        assertThat(p6.getPageNumber()).isEqualTo(6);
        assertThat(p6.getSourcePdfPage()).isEqualTo(6);
        assertThat(p6.getMarkdownContent()).contains("اسم الكتاب");
        assertThat(p6.getMarkdownClean()).doesNotContain("اسم الكتاب");
        assertThat(p6.getPrintedPageLabel()).isEqualTo("٣");
        assertThat(p6.getStatus()).isEqualTo(OcrStatus.COMPLETED);
        assertThat(p6.getOcrModel()).isEqualTo("text-layer");
    }

    @Test void writesTheSectionTreeWithParentsLevelsAndRanges() {
        persister.persist(book, resultFromFixture("book-outline.pdf"));
        List<BookSection> s = capturedSections();
        assertThat(s).extracting(BookSection::getTitle, BookSection::getLevel, BookSection::getStartPage, BookSection::getEndPage)
                .containsExactly(tuple("المقدمة", (short) 1, 4, 5), tuple("الفصل الأول", (short) 1, 6, 9),
                        tuple("المبحث الأول", (short) 2, 7, 7), tuple("المبحث الثاني", (short) 2, 8, 9),
                        tuple("الفصل الثاني", (short) 1, 10, 12), tuple("الخاتمة", (short) 1, 13, 14));
        assertThat(s.get(2).getParent().getTitle()).isEqualTo("الفصل الأول");
        assertThat(s).allSatisfy(x -> assertThat(x.getSource()).isEqualTo(StructureSource.PDF_OUTLINE));
    }

    @Test void eachPageBelongsToTheDeepestSectionThatContainsIt() {
        persister.persist(book, resultFromFixture("book-outline.pdf"));
        assertThat(capturedPages().get(6).getSection().getTitle()).isEqualTo("المبحث الأول");   // page 7
        assertThat(capturedPages().get(5).getSection().getTitle()).isEqualTo("الفصل الأول");    // page 6
        assertThat(capturedPages().get(1).getSection()).isNull();                                 // front matter
    }

    @Test void sharedStartPageGoesToTheLaterSection() {
        // two level-1 entries starting on the same page 6: the page belongs to the second
        persister.persist(book, resultWithEntriesOnSamePage());
        assertThat(capturedPages().get(5).getSection().getTitle()).isEqualTo("الفصل الثاني");
    }

    @Test void bookFieldsAreSetAndErrorsMeanNeedsReview() {
        persister.persist(book, resultFromFixture("book-printed-toc.pdf"));
        assertThat(book.getStructureSource()).isEqualTo(StructureSource.TEXT_LAYER);
        assertThat(book.getStructureStatus()).isEqualTo(StructureStatus.RESOLVED);
        assertThat(book.getPageCount()).isEqualTo(14);
        assertThat(book.getOcrStatus()).isEqualTo(OcrStatus.COMPLETED);

        persister.persist(book, resultWithErrorWarning());
        assertThat(book.getStructureStatus()).isEqualTo(StructureStatus.NEEDS_REVIEW);
    }
}

class NativeExtractTaskletTest {
    @Test void aScannedPdfFailsWithTheNoOcrMessage() throws Exception {
        when(storage.getBytes("k")).thenReturn(fixture("image-only.pdf"));
        assertThatThrownBy(() -> tasklet(7L, "k").execute(contribution, chunkContext))
                .hasMessageContaining("scanned books are not supported while OCR is off");
        assertThat(book.getOcrStatus()).isEqualTo(OcrStatus.FAILED);
    }

    @Test void aRejectedPdfFailsTheBookWithTheReasonInsteadOfSavingAnEmptyBook() throws Exception {
        when(storage.getBytes("k")).thenReturn("hello".getBytes());
        assertThatThrownBy(() -> tasklet(7L, "k").execute(contribution, chunkContext)).hasMessageContaining("NOT_PDF");
        verify(persister, never()).persist(any(), any());
        assertThat(book.getOcrStatus()).isEqualTo(OcrStatus.FAILED);
    }
}
```

- [ ] **Step 2:** run → FAIL.
- [ ] **Step 3: Implement.**
  - **`ExtractionPersister.persist(Book book, BookExtractionResult r)`**, which is `@Transactional`:
    1. Delete the existing rows (`pageRepository.deleteByBook_Id`, then `sectionRepository.deleteByBook_Id`). The router's `BookContentPurger` already purged, but this keeps the job safe to re-run alone.
    2. Save the sections depth-first, with `sortOrder` running 0..n in document order, `titleNormalized = ArabicTextNormalizer.normalize(title)`, `sectionType` from `TocEntryType`, and `source`/`confidence`/`needsReview` set.
    3. **Save pages:** each `BookPage` gets `pageNumber = sourcePdfPage = pdfPage`, `markdownContent = rawText`, `markdownClean = cleanedText`, `printedPageLabel`, `runningHeader`, `status = COMPLETED`, `ocrModel = "text-layer"` and `promptVersion = "native-v1"`. `pageKind` is `TOC` for detected TOC pages, `IMAGE_ONLY` for image-only pages (scanned pages in a hybrid book), `BLANK` for an empty cleaned text, and `BODY` otherwise. `section` is the deepest saved section whose `[start,end]` contains the page; ties go to the later one in `sortOrder`.
    4. **Book fields:** `pageCount`, `structureSource`, `structureStatus` (`RESOLVED`, or `NEEDS_REVIEW` when any warning is ERROR or the source is NONE), `ocrStatus = COMPLETED`, and `tocRaw` = JSON of `{toc, structureDetection, warnings}` for review.
  - **`NativeExtractTasklet`:** gets the PDF bytes from storage by `pdfKey`, runs `extractionService.extract`, then `persister.persist`. On `PdfRejectedException` it sets `book.setOcrStatus(FAILED)`, saves, and rethrows an `IllegalStateException("Native extraction rejected the PDF: " + reason)` so the batch execution is FAILED. For `NO_TEXT_LAYER`, the message is "This PDF has no text layer; scanned books are not supported while OCR is off." It also records the metrics `extraction.native.result{source=…}` and `extraction.native.warnings{code=…}`.
  - **`NativeIngestionBatchConfig.nativeIngestionJob`:** a single `nativeExtractStep` with a `@StepScope` tasklet taking `jobParameters['bookId']` and `['pdfKey']`, mirroring how `StudioBatchConfig` builds its steps.
- [ ] **Step 4:** run → PASS. Then do a local end-to-end run with `KTAB_STUDIO_ENABLED=false`: upload a real digital Arabic book through the normal book upload and confirm, in order:
  1. The router logs route `NATIVE`.
  2. `tbl_book_pages` row count equals the PDF page count.
  3. `tbl_book_sections` holds the TOC.
  4. The reader shows the text.
- [ ] **Step 5: Commit** — `feat(extraction): nativeIngestionJob writes pages and sections into the shared tables`.

---

### Task 6: Native TTS building blocks — chunker, provider, chapter synthesizer

**Files (create, base `com.doova.ktab.features.nativetts`):** `config/NativeTtsProperties.java`, `TextChunker.java`, `ChunkTtsProvider.java`, `ElevenLabsChunkTtsProvider.java`, `ChunkAudio.java`, `ChapterSynthesizer.java`, `MediaTools.java`; tests.

**Produces:**

```java
public record ChunkAudio(byte[] mp3, String chars, double[] startSec, double[] endSec) { }
public interface ChunkTtsProvider { ChunkAudio synthesize(String text, String previousText, String nextText); }
public record ChapterAudio(Path mp3, int durationMs, String chars, int[] startMs, int[] endMs) { }
// TextChunker.chunk(String text, int maxChars) -> List<String>
// ChapterSynthesizer.synthesize(String chapterText, Path workDir) -> ChapterAudio
// MediaTools.durationMs(Path mp3) (ffprobe) ; MediaTools.concat(List<Path> parts, Path out) (ffmpeg -c copy)
```

`NativeTtsProperties` (`ktab.native-tts.*`):
- `voiceId`: required. A blank value fails fast at job start with "set KTAB_NATIVE_TTS_VOICE_ID".
- `modelId`: default `eleven_multilingual_v2` (Task 0 decides).
- `outputFormat`: default `mp3_44100_128`.
- `maxCharsPerRequest`: default 2500.
- `sendContext`: default true.
- `maxRetries`: default 4.
- `maxCharsPerBook`: default 1,500,000.
- `ffmpegPath` and `ffprobePath`: default `ffmpeg` and `ffprobe`.

- [ ] **Step 1: Failing tests.**

```java
class TextChunkerTest {
    @Test void neverSplitsAWordAndNeverExceedsTheLimit() {
        String text = ("كان يا ما كان في قديم الزمان، رجلٌ حكيمٌ يسكن قريةً صغيرة. ").repeat(200);
        List<String> chunks = TextChunker.chunk(text, 500);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(500));
        assertThat(String.join(" ", chunks).replaceAll("\\s+", " ").strip()).isEqualTo(text.replaceAll("\\s+", " ").strip());
        assertThat(chunks).allSatisfy(c -> assertThat(c).doesNotStartWith(" ").doesNotEndWith(" "));
    }
    @Test void prefersParagraphThenSentenceBoundaries() {
        List<String> chunks = TextChunker.chunk("فقرة أولى قصيرة.\n\nفقرة ثانية. وجملة أخرى؟ وثالثة!", 30);
        assertThat(chunks.get(0)).isEqualTo("فقرة أولى قصيرة.");
        assertThat(chunks).allSatisfy(c -> assertThat(c).matches("(?s).*[.!؟?؛…]$"));
    }
    @Test void aSingleWordLongerThanTheLimitIsHardSplitAsALastResort() {
        assertThat(TextChunker.chunk("ا".repeat(25), 10)).hasSize(3);
    }
}

class ChapterSynthesizerTest {
    @Test void offsetsUseMeasuredChunkDurations(@TempDir Path dir) throws Exception {
        // fake provider: 2 chunks with known alignments; fake MediaTools: chunk 1 lasts 1000 ms, chunk 2 lasts 800 ms
        ChunkTtsProvider tts = (text, prev, next) -> text.startsWith("أ")
                ? new ChunkAudio(new byte[]{1}, "أب", new double[]{0.0, 0.4}, new double[]{0.4, 0.9})
                : new ChunkAudio(new byte[]{2}, "جد", new double[]{0.0, 0.3}, new double[]{0.3, 0.7});
        FakeMediaTools media = new FakeMediaTools(List.of(1000, 800));
        ChapterSynthesizer s = new ChapterSynthesizer(tts, media, props(2));   // maxCharsPerRequest = 2 -> two chunks
        ChapterAudio a = s.synthesize("أب جد", dir);
        assertThat(a.chars()).isEqualTo("أب جد");                       // the separator space between chunks is in chars
        assertThat(a.startMs()).containsExactly(0, 400, 1000, 1000, 1300); // space at the boundary gets the boundary time
        assertThat(a.endMs()).containsExactly(400, 900, 1000, 1300, 1700);
        assertThat(a.durationMs()).isEqualTo(1800);
        assertThat(media.concatenated()).hasSize(2);
    }

    @Test void passesNeighbouringTextForContinuityWhenEnabled(@TempDir Path dir) throws Exception {
        List<String[]> calls = new ArrayList<>();
        ChunkTtsProvider tts = (t, p, n) -> { calls.add(new String[]{t, p, n}); return new ChunkAudio(new byte[]{1}, t, new double[t.length()], new double[t.length()]); };
        new ChapterSynthesizer(tts, new FakeMediaTools(List.of(100, 100)), props(2)).synthesize("أب جد", dir);
        assertThat(calls.get(0)).containsExactly("أب", "", "جد");
        assertThat(calls.get(1)).containsExactly("جد", "أب", "");
    }
}

class ElevenLabsChunkTtsProviderTest {
    @Test void retriesOn429ThenParsesAudioAndAlignment() {
        // MockWebServer (okhttp3, add test dependency if missing) or WireMock: first response 429, second 200 with
        // {"audio_base64":"AAEC","alignment":{"characters":["ا","ب"],"character_start_times_seconds":[0,0.2],"character_end_times_seconds":[0.2,0.5]}}
        ChunkAudio a = provider.synthesize("اب", "", "");
        assertThat(a.mp3()).containsExactly(0, 1, 2);
        assertThat(a.chars()).isEqualTo("اب");
        assertThat(server.getRequestCount()).isEqualTo(2);
        RecordedRequest req = server.takeRequest(); // first request
        assertThat(req.getPath()).startsWith("/v1/text-to-speech/voice-1/with-timestamps?output_format=mp3_44100_128");
        assertThat(req.getHeader("xi-api-key")).isEqualTo("key");
    }
    @Test void a400IsNotRetriedAndFailsWithTheBody() { /* 400 {"detail":"text too long"} -> IllegalStateException containing "text too long", 1 request */ }
}
```

- [ ] **Step 2:** run → FAIL.
- [ ] **Step 3: Implement.**
  - **`TextChunker.chunk(text, max)`:** greedy fill. Split into paragraphs (`\n\s*\n`). A paragraph that fits is merged with the following ones while the total fits, joined with `\n\n`. A paragraph that is too long is split at sentence ends (`(?<=[.!؟?؛…])\s+`) the same way. A sentence that is too long is split at whitespace. A single token that is too long is hard-split at `max`. Every chunk is stripped, and empty chunks are dropped.
  - **`ElevenLabsChunkTtsProvider`:** a blocking `WebClient` to `{baseUrl}/v1/text-to-speech/{voiceId}/with-timestamps?output_format={fmt}`, with the header `xi-api-key` from `ELEVENLABS_API_KEY` (the existing `elevenlabs.api-key` property).
    - **Body:** `{text, model_id, previous_text?, next_text?}`, with the context fields only when `sendContext`.
    - **Retry:** 429 and 5xx retry with exponential backoff (2, 4, 8, 16 s) up to `maxRetries`. Other 4xx fail at once with the response body.
    - **Parsing:** decode `audio_base64`. `chars` = the characters joined; the start and end arrays come from `alignment.character_start_times_seconds` / `character_end_times_seconds`.
  - **`ChapterSynthesizer.synthesize(text, workDir)`:** chunk the text, then for each chunk:
    1. Call the provider with the neighbouring chunks as context.
    2. Write `part-{i:04d}.mp3` and measure its duration with `MediaTools.durationMs`.
    3. Append the chunk's alignment shifted by the cumulative offset (ms, rounded).
    4. Between chunks, append one space char with `start = end = offset` at the boundary.

    Then `MediaTools.concat(parts, workDir/chapter.mp3)` with `ffmpeg -y -f concat -safe 0 -i list.txt -c copy`. `durationMs` = the sum of the measured durations.
  - **`MediaTools`:** runs `ProcessBuilder` with a 120 s timeout and reads stderr on failure. `durationMs` runs `ffprobe -v error -show_entries format=duration -of csv=p=0` and rounds to ms.
- [ ] **Step 4:** run → PASS. Then one real smoke test (`@EnabledIfEnvironmentVariable(named = "NATIVE_TTS_LIVE", matches = "true")`): synthesize 2 short Arabic paragraphs with `maxCharsPerRequest = 60`. Assert the MP3 duration is within 300 ms of the last `endMs`, and that `chars` equals the input with whitespace collapsed.
- [ ] **Step 5: Commit** — `feat(nativetts): chunker, ElevenLabs with-timestamps provider and chapter synthesizer`.

---

### Task 7: `nativeAudiobookJob` — same outputs as Studio

**Files (create):** `features/nativetts/batch/{NativeAudiobookBatchConfig,NativeEstimateTasklet,NativeSynthesizeTasklet}.java`; test `NativeSynthesizeTaskletTest`.

**Consumes:** `ChapterSynthesizer`, `BookSectionRepository.findByBook_IdAndParentIsNullOrderBySortOrderAsc`, `BookPageRepository.findByBookIdOrderByPageNumberAsc`, `BookAudioChapterRepository` (existing), and the `S3Client` and bucket the same way `StudioBatchConfig` gets them (`@Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}")`). It also reuses `TimingIndexTasklet.ColumnarTimingPayload` (a public record) for the identical format.

- [ ] **Step 1: Failing tests.**

```java
class NativeSynthesizeTaskletTest {
    @Test void oneAudioChapterPerTopLevelSectionAtTheStudioKeys() throws Exception {
        givenSections(level1("المقدمة", 4, 5), level1("الفصل الأول", 6, 9));
        givenPages(textPages(4, 9));
        tasklet(7L).execute(contribution, chunkContext);
        verify(s3).putObject(argThat((PutObjectRequest r) -> r.key().equals("audio/7/chapters/ch-0001.mp3")), any(RequestBody.class));
        verify(s3).putObject(argThat((PutObjectRequest r) -> r.key().equals("audio/7/timings/ch-0001.json.gz")
                && "gzip".equals(r.contentEncoding())), any(RequestBody.class));
        List<BookAudioChapter> saved = savedAudioChapters();
        assertThat(saved).extracting(BookAudioChapter::getSortOrder).containsExactly(1, 2);
        assertThat(saved.get(1).getBookSection().getTitle()).isEqualTo("الفصل الأول");
        assertThat(saved.get(1).getSha256()).hasSize(64);
        assertThat(book.getHasAudio()).isTrue();
    }

    @Test void chapterTextIsItsPagesCleanTextIncludingSubsectionPages() throws Exception {
        givenSections(level1WithChild("الفصل الأول", 6, 9, "المبحث الأول", 7, 7));
        givenPages(textPages(6, 9));
        tasklet(7L).execute(contribution, chunkContext);
        verify(synthesizer).synthesize(argThat(t -> t.contains("page-6") && t.contains("page-7") && t.contains("page-9")), any());
    }

    @Test void resumeSkipsFinishedChapters() throws Exception {
        givenSections(level1("المقدمة", 4, 5), level1("الفصل الأول", 6, 9));
        givenExistingAudio(1);   // ch-0001 row exists and its object exists in storage
        tasklet(7L).execute(contribution, chunkContext);
        verify(synthesizer, times(1)).synthesize(any(), any());
    }

    @Test void aBookWithNoSectionsBecomesOneChapter() throws Exception {
        givenSections();
        givenPages(textPages(1, 3));
        tasklet(7L).execute(contribution, chunkContext);
        assertThat(savedAudioChapters()).singleElement().satisfies(c -> assertThat(c.getBookSection()).isNull());
    }

    @Test void hasAudioStaysFalseIfAnyChapterFails() throws Exception {
        givenSections(level1("المقدمة", 4, 5), level1("الفصل الأول", 6, 9));
        when(synthesizer.synthesize(contains("page-6"), any())).thenThrow(new IllegalStateException("tts down"));
        assertThatThrownBy(() -> tasklet(7L).execute(contribution, chunkContext)).hasMessageContaining("tts down");
        assertThat(book.getHasAudio()).isFalse();
    }
}
```

If `BookAudioChapter.bookSection` is non-nullable in the entity or the migration, the "no sections" case instead creates one synthetic level-1 section titled with the book title during extraction (Task 5). Check `BookAudioChapter` and the V-migration that created `tbl_book_audio_chapters`, and pick the matching branch.

- [ ] **Step 2:** run → FAIL.
- [ ] **Step 3: Implement.**
  - **`NativeEstimateTasklet`:** sum the clean-text characters of the pages. Fail when the total is over `maxCharsPerBook`, or when `voiceId` is blank. Put the total in the job context and log the estimated credits.
  - **`NativeSynthesizeTasklet`:** chapters are the top-level sections in `sortOrder`, numbered `sortOrder = index + 1`. Each chapter's text is the `markdownClean` of the pages whose `section` is that section or one of its descendants, in page order, joined with `\n\n`, with the section title prepended as the first line. Then, per chapter:
    1. Skip it if a `BookAudioChapter(book, sortOrder)` exists and `s3.headObject(audioKey)` succeeds.
    2. Otherwise synthesize in a temp dir under `java.io.tmpdir`.
    3. Upload the MP3 (`audio/mpeg`) and the gzipped `ColumnarTimingPayload(1, "native-" + sectionId, chars, startMs, endMs)` (`application/gzip`, `contentEncoding=gzip`).
    4. Upsert the `BookAudioChapter` (`audioPath`, `timingsPath`, `durationMs`, `sizeBytes`, `sha256` of the MP3).
    5. Delete the temp dir.

    After all chapters, set `book.setHasAudio(true)` and save. The metrics are `audiobook.native.chapters{outcome}` and `audiobook.native.chars`.
  - **`nativeAudiobookJob`:** `nativeEstimateStep` → `nativeSynthesizeStep`, built like `studioAudiobookJob` with `@StepScope` tasklets on `jobParameters['bookId']`.
- [ ] **Step 4:** run → PASS.
- [ ] **Step 5: Commit** — `feat(nativetts): nativeAudiobookJob writes Studio-compatible audio and timings`.

---

### Task 8: One audiobook endpoint, chosen by the flag

**Files:** create `features/audiobook/AudiobookLauncher.java`; modify `StudioJobController.java`; test `AudiobookLauncherTest`.

**Produces:** `AudiobookLauncher.launch(Long bookId) -> JobExecution`, `AudiobookLauncher.runningFor(Long bookId) -> Optional<JobExecution>`, and `AudiobookLauncher.activeJobName() -> String`.

- [ ] **Step 1: Failing tests.**

```java
class AudiobookLauncherTest {
    @Test void studioJobWhenEnabledNativeJobWhenDisabled() throws Exception {
        studioProps.setEnabled(true);
        launcher.launch(7L);
        verify(jobLauncher).run(eq(studioAudiobookJob), any());

        studioProps.setEnabled(false);
        launcher.launch(7L);
        verify(jobLauncher).run(eq(nativeAudiobookJob), any());
    }

    @Test void aRunningJobOfEitherKindBlocksANewOne() {
        when(jobExplorer.findRunningJobExecutions("nativeAudiobookJob")).thenReturn(Set.of(execFor(7L)));
        assertThat(launcher.runningFor(7L)).isPresent();
    }
}
```

- [ ] **Step 2:** run → FAIL.
- [ ] **Step 3: Implement.**
  - **`AudiobookLauncher`:** injects both jobs, `JobLauncher`, `JobExplorer` and `StudioProperties`. `runningFor` checks both job names.
  - **`StudioJobController.startAudiobook`:** uses `audiobookLauncher.runningFor` and `launch`; its response adds `"pipeline": "STUDIO"|"NATIVE"`.
  - **`getStatus`:** when Studio is disabled or there's no project, it returns `hasAudio`, `pipeline: "NATIVE"`, the running execution (if any), and the list of `BookAudioChapter` rows (`sortOrder`, section title, `durationMs`). The path stays `/api/studio/...`, so the frontend doesn't change.
- [ ] **Step 4:** `./mvnw -q test -Dtest='AudiobookLauncherTest,com.doova.ktab.features.studio.**,com.doova.ktab.features.nativetts.**,com.doova.ktab.features.extraction.**,com.doova.ktab.features.ingestion.**'` → PASS.
- [ ] **Step 5: Commit** — `feat(audiobook): one endpoint launches Studio or native audiobook by KTAB_STUDIO_ENABLED`.

---

### Task 9: End-to-end on real books, config, and the switch-over notes

**Files:** `.env` (local), `application.properties`, create `docs/extraction/switch-over.md`.

- [ ] **Step 1: Config keys** in `application.properties`:

```properties
ktab.studio.enabled=${KTAB_STUDIO_ENABLED:false}
ktab.ocr.enabled=${KTAB_OCR_ENABLED:false}
ktab.extraction.max-upload-bytes=${KTAB_EXTRACTION_MAX_UPLOAD_BYTES:209715200}
ktab.native-tts.voice-id=${KTAB_NATIVE_TTS_VOICE_ID:}
ktab.native-tts.model-id=${KTAB_NATIVE_TTS_MODEL_ID:eleven_multilingual_v2}
ktab.native-tts.max-chars-per-request=${KTAB_NATIVE_TTS_MAX_CHARS:2500}
ktab.native-tts.send-context=${KTAB_NATIVE_TTS_SEND_CONTEXT:true}
ktab.native-tts.max-chars-per-book=${KTAB_NATIVE_TTS_MAX_CHARS_PER_BOOK:1500000}
```

Set `.env` with the Task 0 values, `KTAB_STUDIO_ENABLED=false` and `KTAB_OCR_ENABLED=false`. Leave `KTAB_NATIVE_TTS_VOICE_ID` empty until the owner chooses the narrator: the audiobook job refuses to start without it, so nothing is spent by accident. For the Step 2 test run, set it temporarily to any approved Arabic voice and clear it afterwards.

- [ ] **Step 2: Real run.** With the app on this branch:
  1. Upload 3 real digital Arabic books (one with bookmarks, one with only a printed فهرس, one with neither), plus one scanned book. The scanned one must fail with the "no text layer… OCR is off" message, and `ocrJob` must not appear in the batch tables.
  2. For each, record the route, `structure_source`, the confidence, the warnings, the chapter count against the book's real chapters, and three spot checks of chapter start pages.
  3. Run `POST /api/studio/books/{id}/audiobook` on the shortest book, then play `ch-0001.mp3`.
  4. Load its timing index in the reader and check the highlight follows the voice at the start, middle and end of the chapter (drift under 300 ms).
- [ ] **Step 3: `docs/extraction/switch-over.md`:**
  - **When Studio access arrives:** set `KTAB_STUDIO_ENABLED=true` and restart. New DIGITAL books go to Studio, and new audiobook requests use Studio. Books already ingested natively keep their pages; re-ingesting one (the existing re-ingest admin endpoint) re-runs it through Studio.
  - **Rollback:** set `KTAB_STUDIO_ENABLED=false`.
  - **Turning OCR back on later:** set `KTAB_OCR_ENABLED=true`. Scanned books route to `ocrJob` again, exactly as before this change.
  - **What each route writes:** the output contract above, for reference.
- [ ] **Step 4: Commit** — `docs(extraction): switch-over and rollback; config defaults`.

---

## Decisions taken (change before execution if you disagree)

- **One flag for both jobs** (`KTAB_STUDIO_ENABLED`, default **false**), as asked. It's read at launch time only.
- **The native pipeline writes the existing shared tables and audio keys**, so no migration is needed and the reader, search, talk-to-book and trailer work unchanged.
- **OCR is off** (`KTAB_OCR_ENABLED=false`). Every non-Studio book goes native. Fully scanned books fail with a clear message, and scanned pages inside hybrid books are kept empty and reported.
- **Audiobook chapters = the top-level sections**, as whole pages (mid-page chapter starts are a known V1 limitation).
- **Audiobook voice:** one voice per book from `KTAB_NATIVE_TTS_VOICE_ID`, chosen later by the owner. It has no default, and the job refuses to start while it is blank. Studio's default paragraph voice (`21m00Tcm4TlvDq8ikWAM`) is an English voice and isn't reused.
- **`POST /api/books/extract`** is for admins and librarians, and returns pages only with `?includePages=true`.

## Open decisions for the product owner

1. **Narrator voice:** the owner will specify `KTAB_NATIVE_TTS_VOICE_ID` later. Task 0 still records whether `eleven_v3` or `eleven_multilingual_v2` gives timings and context, so the model can be chosen along with the voice.
