# OCR Engine v3 → PDF Classification, Dual-Pipeline Ingestion & Studio Audiobook

## Problem Statement

v2 treats every PDF identically: render all pages to PNG at 300 DPI, upload each to R2, call Gemini page-by-page, then resolve structure. For a born-digital PDF this is pure waste — the text is already in the file, perfectly, for free. We pay Gemini per page and write ~1.5 GB to R2 per 300-page book to recover text that `PDFTextStripper` would have returned in two seconds.

v3 adds:

1. **Classification** — decide up front whether a PDF is `DIGITAL`, `SCANNED`, `HYBRID_OCR` or `MIXED`, before any rendering happens.
2. **A second, fully independent ingestion pipeline** for digital PDFs, built on ElevenLabs Studio, which never touches the OCR pipeline.
3. **Audiobook generation** — one-time Studio conversion per book, with character-level timings stored for text/audio synchronisation in the reader.

---

## Two core principles

### 1. Ktab never infers correspondence

Two independent parsers produce two independent structures, and reconciling them is a permanent, silently-degrading data-quality problem. v3 refuses the problem rather than solving it.

> **Either Studio creates the structure and Ktab adopts it, or Ktab creates the structure and pushes it. The link is written at the moment of creation and stored as an id.**

No fuzzy matching, no similarity thresholds, no confidence scores on links, no review queue for alignment. This is enforced in the schema — `tbl_studio_chapters.col_book_section_id` is `NOT NULL` — not by convention.

What this deletes, versus an alignment-based design:

| dropped | reason |
|---|---|
| Chapter↔section fuzzy matcher | Studio's chapter ids are recorded on our section rows at creation. |
| Anchoring pass (normalise → sliding window → fuzzy fallback) | Page text is *built from* node text, so offsets are known exactly. |
| `col_link_confidence`, `col_needs_review`, `UNMATCHED` rows, drift metrics | Nothing to be uncertain about. |
| Markdown reconstruction for digital PDFs (font-size heading inference, column detection, de-hyphenation) | Studio already parses the PDF into `h1/h2/h3/p` blocks. Running our own parser in parallel is what created the two-structures problem. |

### 2. Two independent pipelines, one output contract

The Studio pipeline is **not a branch of the OCR job**. It is a separate Spring Batch job, in a separate package, with separate config, separate metrics and separate quality gates, and it imports nothing from `features.ocr`.

What is *not* independent — deliberately — is storage. Both pipelines write `tbl_book_pages`, `tbl_book_sections` and `tbl_books`. Giving the Studio route its own content tables would force two code paths through [BookTextController](../src/main/java/com/doova/ktab/controller/v1/reader/BookTextController.java), in-book search and the section tree, and every future reader feature would have to be built twice. **The tables are a contract, not a coupling.**

```
features/ingestion/   ← classifier + router only
features/ocr/         ← existing, untouched
features/studio/      ← new, standalone
```

The consequence worth stating plainly: **[OcrBatchConfig](../src/main/java/com/doova/ktab/features/ocr/config/OcrBatchConfig.java) does not change.** No `FlowBuilder`, no `.on("STUDIO")` branch, no edits to the job definition that currently runs every production book. `ocrJob` keeps its name, steps, `JobInstance` history and behaviour, so nothing in [OcrJobController](../src/main/java/com/doova/ktab/features/ocr/web/OcrJobController.java) or `OcrProgressController` needs touching.

---

## Authority by route

| PDF type | pipeline | text + structure authority | ElevenLabs role | link established by |
|---|---|---|---|---|
| `DIGITAL` | `studioIngestionJob` | **Studio** | parser + TTS | Studio's `chapter_id`, stored on our section row |
| `SCANNED` | `ocrJob` | Ktab OCR | TTS only | our `section_id`, sent when creating the chapter |
| `HYBRID_OCR` | `ocrJob` | Ktab OCR | TTS only | as above |
| `MIXED` | `ocrJob` | Ktab OCR | TTS only | as above |
| `UNKNOWN` | `ocrJob` | Ktab OCR | TTS only | as above |

`HYBRID_OCR → OCR` is a **cost decision, not a technical one**. A hybrid page's text layer is somebody else's OCR — frequently Tesseract at default settings on Arabic — and ours is better. Routing it to OCR is the conservative default; it is configurable and should be revisited once shadow-mode data shows how good those layers actually are.

---

## Phase 0 — Contracts and foundations

### Enums

```java
package com.doova.ktab.enums.book;

public enum PdfType        { DIGITAL, SCANNED, HYBRID_OCR, MIXED, UNKNOWN }
public enum IngestionRoute { STUDIO, OCR }
```

`UNKNOWN` is load-bearing: encrypted PDFs, `Loader.loadPDF` failures, zero-page documents. **Never default to `DIGITAL`** — an unreadable PDF must route to OCR. Failing safe here costs money; failing unsafe ships a broken book.

Also needed: `StructureSource.STUDIO`, and `SectionType.CHAPTER` if absent.

### Properties, split by owner

The classifier belongs to neither pipeline — it sits above both — so it does **not** extend `OcrProperties`.

```java
@ConfigurationProperties(prefix = "ktab.ingestion")   // features/ingestion
public class IngestionProperties {
    private Classification classification = new Classification();

    public static class Classification {
        private boolean enabled = true;
        private boolean shadowMode = true;       // classify, record, route everything to OCR anyway
        private int     minCharsPerPage = 50;
        private int     maxSampledPages = 40;    // 0 = scan all
        private int     fullScanUnderPages = 60;
        private double  imageCoverageThreshold = 0.80;
        private int     minImagePixelsWidth = 700;
        private int     minImagePixelsHeight = 900;
        private double  digitalRatio = 0.70;
        private double  scannedRatio = 0.70;
        private double  hybridRatio = 0.70;
        private double  arabicSanityRatio = 0.60;
        private int     formXObjectMaxDepth = 8;
        private Duration timeout = Duration.ofSeconds(60);
        private Map<PdfType, IngestionRoute> routing = defaultRouting();
    }
}

@ConfigurationProperties(prefix = "ktab.studio")      // features/studio
public class StudioProperties { /* voices, model, page size, gate thresholds, cost caps */ }
```

[OcrProperties](../src/main/java/com/doova/ktab/features/ocr/config/OcrProperties.java) is untouched.

### Migrations

`V11__pdf_classification.sql` — on `tbl_books`:

| column | type | note |
|---|---|---|
| `col_pdf_type` | `varchar(20)` | null until classified |
| `col_ingestion_route` | `varchar(20)` | resolved route; admin-overridable |
| `col_pdf_classification` | `jsonb` | per-page evidence, sampling info |
| `col_classifier_version` | `varchar(10)` | so a corpus can be re-classified after tuning |

Persist the evidence blob. When a book turns out misrouted six months from now, you need to know *why* without re-running the classifier against a PDF that may have been replaced.

`V12__studio_audiobook.sql` — the three tables and four columns in Phase 3.

All tables follow the house convention from [V6__book_structure.sql](../src/main/resources/db/migration/V6__book_structure.sql): `tbl_*` / `col_*`, plus the BaseEntity block — `col_created_by`, `col_last_modified_by`, `created_at`, `updated_at`, `version INTEGER NOT NULL DEFAULT 0`. `version` is mandatory; [BaseEntity](../src/main/java/com/doova/ktab/model/base/BaseEntity.java) declares it `@Version` non-null.

### OcrStatus

Do **not** add a `SKIPPED_DIGITAL` value. `OcrStatus` already carries a redundant `COMPLETED`/`DONE` pair; don't widen it further.

Accept one piece of naming debt: `col_ocr_status` on a book that never saw OCR. It is already used as a general content-readiness flag — `OcrEventListener` sets `PENDING`/`FAILED`, `QualityGateTasklet` sets `COMPLETED`/`FLAGGED` — so the Studio pipeline sets it too. Renaming touches 17+ files for cosmetics; not worth it now.

---

## Phase 1 — `PdfTypeClassifier`

Lives in `features/ingestion/pdf/`. The draft algorithm (per page: enough text? large image? → bucket → ratios) is the right shape. These are the production deltas.

### 1.1 Recurse into Form XObjects — the critical fix

`PDResources.getXObjectNames()` does not recurse. Scanner drivers (Xerox, Canon, HP) and anything that has been through Ghostscript routinely nest the page bitmap inside a `PDFormXObject`. Those pages report "no large image" → classify as `DIGITAL` → skip OCR → we extract a nonexistent or garbage text layer and ship a broken book.

**This is a false negative in the dangerous direction and it is the single most important item in Phase 1.**

```java
private void collectImages(PDResources res, List<PDImageXObject> out, int depth, Set<COSBase> seen) {
    if (res == null || depth > maxDepth) return;
    for (COSName name : res.getXObjectNames()) {
        PDXObject xo;
        try { xo = res.getXObject(name); }
        catch (IOException e) { continue; }          // one bad XObject must not kill the page
        if (xo instanceof PDImageXObject img) {
            out.add(img);
        } else if (xo instanceof PDFormXObject form && seen.add(form.getCOSObject())) {
            collectImages(form.getResources(), out, depth + 1, seen);
        }
    }
}
```

The `seen` set keyed on `COSBase` identity is a cycle guard. Malformed PDFs do contain self-referencing form trees, and without it they stack-overflow the batch thread.

### 1.2 Rendered coverage, not aspect-ratio matching

`Math.abs(pageAR - imageAR) < 0.15` breaks on:

- **rotated pages** — `page.getRotation()` of 90/270 inverts the page ratio but not the image's
- **cropped scans** — trimmed to content, ratio no longer matches
- **tiled scans** — split into horizontal strips by the scanner
- and it misfires *positively* on digital pages carrying a full-bleed cover photo

What matters is the image's **placed area on the page**, which comes from the CTM in the content stream. Subclass `PDFStreamEngine`:

```java
final class ImageCoverageEngine extends PDFStreamEngine {
    private double maxCoverage;
    private long   contributingPixels;
    private final double pageArea;   // from getCropBox(), see below

    @Override
    protected void processOperator(Operator op, List<COSBase> operands) throws IOException {
        if ("Do".equals(op.getName())) {
            PDXObject xo = getResources().getXObject((COSName) operands.getFirst());
            if (xo instanceof PDImageXObject img) {
                Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                double placed = Math.abs(ctm.getScalingFactorX() * ctm.getScalingFactorY());
                double coverage = placed / pageArea;
                if (coverage > maxCoverage) {
                    maxCoverage = coverage;
                    contributingPixels = (long) img.getWidth() * img.getHeight();
                }
            }
        }
        super.processOperator(op, operands);
    }
}
```

A page counts as image-bearing when `maxCoverage >= 0.80` **and** the contributing image is at least 700×900 px. Coverage alone lets a stretched low-res watermark pass; pixel count alone lets a high-res inset figure pass. Both together is the test.

Use **`page.getCropBox()`**, not `getMediaBox()`, for `pageArea`. The crop box is what actually renders, and scanned PDFs frequently carry an oversized media box.

### 1.3 Fix the ratio denominator

In the draft, a page with neither text nor a large image — blank versos, section dividers, plate pages — increments no counter but still counts in `totalPages`. A book that is 35% blank can never reach `0.70` in any bucket and always falls through to `MIXED` → full OCR. Scanned books are exactly the ones with many blank versos, so this leaks money on the books least able to afford it.

```java
int classifiable = digitalPages + scannedPages + hybridPages;
if (classifiable == 0) return Result.unknown("no classifiable pages");
double digitalRatio = (double) digitalPages / classifiable;
```

Record `blankPages` in the evidence JSON. If `classifiable < 0.5 * totalPages`, force `UNKNOWN`.

### 1.4 Gate the text layer on quality, not length

`text.length() > 50` proves a text layer exists, not that it is usable. Three failure modes hit Arabic books hard:

- **Broken CID→Unicode maps.** Embedded subset fonts with no `ToUnicode` extract as `\u0000` runs or Latin gibberish. Length passes; content is worthless.
- **Foreign OCR layers.** A `HYBRID_OCR` page's text is someone else's OCR output.
- **Presentation forms.** Arabic extracts as isolated presentation forms (U+FE70–FEFF) needing normalisation.

The check already exists — [TextLayerTocSource.java:94-111](../src/main/java/com/doova/ktab/features/ocr/structure/TextLayerTocSource.java#L94-L111). **Extract `passesArabicSanityCheck` into a shared `TextLayerQualityAssessor`** (in `util/text/`, reachable by both pipelines) so the classifier and the TOC source cannot drift apart.

Extend it with:

- replacement/null char ratio (`�`, `\u0000`) must be `< 2%`
- presentation-form ratio `> 20%` → flag `needsNormalization` (still usable; route through `ArabicTextNormalizer`)
- alphanumeric-to-total ratio, to reject pages that are pure whitespace and punctuation

Don't hardcode Arabic: read `book.getLanguage()` and select the expected Unicode block, defaulting to the Arabic profile when null.

**Set `stripper.setSortByPosition(true)`.** Without it, multi-column and RTL pages extract in content-stream order, which scrambles the text and tanks our own sanity check.

### 1.5 Sample, don't scan

At 300 pages we parse 300 content streams twice (text + coverage engine) — budget 15–40 s. Sample deterministically:

- all of the first 10 pages (front matter is unrepresentative, but cheap and worth seeing)
- evenly-spaced pages up to `maxSampledPages`
- always the last 3
- full scan under `fullScanUnderPages` (60), or when `maxSampledPages = 0`

Record `sampledPages` and `totalPages` in the evidence.

### 1.6 Robustness

- Per-page work in try/catch; a throwing page increments `errorPages` and does not abort. `errorPages > 20%` → `UNKNOWN`.
- `doc.isEncrypted()` — PDFBox opens with the empty password automatically. If it opened but `getCurrentAccessPermission().canExtractContent()` is false, we are both legally and technically blocked from the text layer → `SCANNED`.
- Hard wall-clock cap (`classification.timeout`); on expiry return `UNKNOWN`.
- Stream the PDF from R2 as [PageImagePreparationTasklet.java:64-66](../src/main/java/com/doova/ktab/features/ocr/batch/PageImagePreparationTasklet.java#L64-L66) does. Note that `RandomAccessReadBuffer` buffers the whole file in heap; for 300-page books prefer `RandomAccessReadBufferedFile` against a temp file, and apply the same change to the preparation tasklet.

---

## Phase 2 — The router

Classification runs **before any rendering**. [PageImagePreparationTasklet.java:75-122](../src/main/java/com/doova/ktab/features/ocr/batch/PageImagePreparationTasklet.java#L75-L122) renders every page at 300 DPI and uploads each PNG to R2 — roughly 1.5 GB of writes for a 300-page book, before a single Gemini call. That is where the savings are.

### 2.1 A component, not a flow

```java
@Component
@RequiredArgsConstructor
public class IngestionRouter {

    private final PdfTypeClassifier classifier;
    private final BookContentPurger purger;
    private final JobLauncher jobLauncher;
    @Qualifier("ocrJob")              private final Job ocrJob;
    @Qualifier("studioIngestionJob")  private final Job studioIngestionJob;

    public JobExecution ingest(Long bookId, String pdfKey) { /* classify → persist → purge → launch */ }
}
```

[OcrEventListener](../src/main/java/com/doova/ktab/event/listener/OcrEventListener.java) delegates to the router instead of launching `ocrJob` directly. Its existing duplicate-run guard needs a second `findRunningJobExecutions("studioIngestionJob")` check.

### 2.2 Purge before write — mandatory

`col_page_number` must be contiguous 1..n per book. If the projection gate fails and a book is rerouted to OCR, Studio's pages and sections are already in the tables; running OCR on top produces duplicate or non-contiguous page numbers.

`BookContentPurger.purge(bookId)` deletes pages, sections and Layer 1 rows (Phase 3). It is the **mandatory first step of both jobs**, not something the reroute path is trusted to remember. `tbl_book_audio_chapters` is excluded unless audio is explicitly being regenerated.

### 2.3 Enforcing independence

`features/studio` must not import `features.ocr`. Two small moves make that true, and an ArchUnit rule keeps it true:

| dependency | action |
|---|---|
| `S3OcrStorageService.getStream` / `generatePresignedUrl` | **misnamed, not OCR-specific** — extract an `ObjectStorageService` interface; the class implements both |
| `ArabicTextNormalizer` | move `features/ocr/text/` → `util/text/` |
| `AttachmentService` (PDF_SOURCE lookup) | already a shared service |
| model + repository layer | shared by definition |

Not needed by Studio at all: `SectionClassifier`, `PageLabelParser`, `RepetitionDetector`, `TocSource`, the whole `image` package.

### 2.4 Escape hatches — build these on day one

- `POST /ingestion/books/{id}/classify` — re-run classification only, return the evidence JSON.
- `POST /ingestion/books/{id}/route?route=OCR|STUDIO` — admin override, persisted, **respected on every subsequent run**. The safety valve when a book classifies wrong in production.
- Expose `pdfType` / `ingestionRoute` on the admin book response so support can see routing without a DB query.

---

## Phase 3 — The database

Three layers. Two throwaway tables for ingestion state, one durable table for what readers stream, and the existing shared tables for content.

### 3.1 Layer 1 — ingestion state (Studio-specific, purgeable)

```sql
CREATE TABLE tbl_studio_projects (
    col_id                    BIGSERIAL    PRIMARY KEY,
    col_book_id               BIGINT       NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_external_project_id   VARCHAR(64)  NOT NULL UNIQUE,
    col_lifecycle             VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
        -- PENDING|CREATED|SYNCED|CONVERTING|CONVERTED|ARCHIVED|FAILED|DELETED
    col_project_deleted_at    TIMESTAMPTZ,          -- NULL => possible orphan at ElevenLabs
    col_model_id              VARCHAR(100),
    col_title_voice_id        VARCHAR(64),
    col_paragraph_voice_id    VARCHAR(64),
    col_quality_preset        VARCHAR(30),
    col_pronunciation_dicts   JSONB,                -- [{id, versionId}] — reproducibility
    col_raw_content_path      TEXT,                 -- R2 key: full chapter JSON dump
    col_last_synced_at        TIMESTAMPTZ,
    col_sync_error            TEXT,
    ... BaseEntity ...
);

CREATE UNIQUE INDEX uq_studio_projects_live_book ON tbl_studio_projects (col_book_id)
    WHERE col_lifecycle NOT IN ('DELETED', 'FAILED', 'ARCHIVED');

CREATE TABLE tbl_studio_chapters (
    col_id                     BIGSERIAL    PRIMARY KEY,
    col_project_id             BIGINT       NOT NULL REFERENCES tbl_studio_projects (col_id) ON DELETE CASCADE,
    col_external_chapter_id    VARCHAR(64)  NOT NULL,
    col_book_section_id        BIGINT       NOT NULL REFERENCES tbl_book_sections (col_id),
    col_origin                 VARCHAR(20)  NOT NULL,   -- STUDIO_PROJECTED | KTAB_PUSHED
    col_order_index            INT          NOT NULL,
    col_content_hash           CHAR(64),
    col_conversion_progress    NUMERIC(4,3) CHECK (col_conversion_progress BETWEEN 0 AND 1),
    col_last_conversion_error  TEXT,
    col_deleted_at             TIMESTAMPTZ,            -- tombstone; never hard-delete
    ... BaseEntity ...,
    CONSTRAINT uq_studio_chapters_external UNIQUE (col_project_id, col_external_chapter_id),
    CONSTRAINT uq_studio_chapters_order    UNIQUE (col_project_id, col_order_index)
        DEFERRABLE INITIALLY DEFERRED
);
```

Three things here are load-bearing:

**`col_book_section_id NOT NULL`** makes "never infer correspondence" a schema property rather than a convention someone violates in six months. A chapter row cannot exist without a known section.

**`DEFERRABLE INITIALLY DEFERRED` on the order constraint.** A plain unique is a live bug: the moment Studio reorders chapters 2 and 3 and we apply updates row by row, the first UPDATE collides with the not-yet-updated second row and the sync transaction aborts. The alternative — a two-pass shuffle through negative indexes — is worse.

**`col_content_hash`** makes polling affordable. Without it, every 5-second poll during a 20-minute conversion rewrites the whole subtree — 60+ times over.

**`col_lifecycle` is ours; Studio's `state` is not mirrored.** Their state vocabulary is opaque and can change under us. Never branch on it.

### 3.2 Layer 2 — durable audio (vendor-neutral, what readers query)

```sql
CREATE TABLE tbl_book_audio_chapters (
    col_id              BIGSERIAL PRIMARY KEY,
    col_book_id         BIGINT NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_book_section_id BIGINT NOT NULL REFERENCES tbl_book_sections (col_id) ON DELETE CASCADE,
    col_sort_order      INT    NOT NULL,
    col_audio_path      TEXT   NOT NULL,   -- R2: audio/{bookId}/chapters/ch-0001.mp3
    col_timings_path    TEXT,              -- R2: gzipped {pageId,charStart,charEnd,startMs,endMs}[]
    col_duration_ms     INT    NOT NULL,
    col_size_bytes      BIGINT NOT NULL,
    col_sha256          CHAR(64) NOT NULL,
    ... BaseEntity ...,
    UNIQUE (col_book_id, col_sort_order)
);
```

This is the important boundary. **Layer 1 is scaffolding; Layer 2 is the product.** After `cleanupStep` deletes the Studio project, the Layer 1 rows can be dropped entirely and every audiobook keeps working. Readers never join against a table containing `external_project_id`, so the vendor does not leak into the read path — and the OCR-route push path writes the same table.

### 3.3 Layer 3 — shared content tables (existing, two writers)

No new tables. Four columns, which are what make projection idempotent:

```sql
ALTER TABLE tbl_book_sections
    ADD COLUMN col_external_chapter_id VARCHAR(64);
CREATE UNIQUE INDEX uq_book_sections_external_chapter
    ON tbl_book_sections (col_book_id, col_external_chapter_id)
    WHERE col_external_chapter_id IS NOT NULL;

ALTER TABLE tbl_book_pages
    ADD COLUMN col_external_chapter_id  VARCHAR(64),
    ADD COLUMN col_chapter_page_ordinal INT;
CREATE UNIQUE INDEX uq_book_pages_external_chapter_ordinal
    ON tbl_book_pages (col_book_id, col_external_chapter_id, col_chapter_page_ordinal)
    WHERE col_external_chapter_id IS NOT NULL;
```

Storing Studio's external ids **on our own rows** turns projection into `INSERT ... ON CONFLICT DO UPDATE` keyed on ids Studio guarantees stable. Re-sync a book a hundred times: identical `col_id`s, nothing downstream breaks. Without these, every re-sync issues new ids and anything saved against them breaks.

### 3.4 Layer 4 — R2, not Postgres

| artifact | key |
|---|---|
| raw Studio chapter JSON (provenance, replay) | `studio/{bookId}/chapters.json.gz` |
| chapter audio | `audio/{bookId}/chapters/ch-{sortOrder:04d}.mp3` |
| timing + offset index | `audio/{bookId}/timings/ch-{sortOrder:04d}.json.gz` |

A 300-page book is 500k–900k characters; character-level timings at ~40 bytes of JSON each is 20–35 MB **per book**, write-once and read-sequentially. Store columnar and gzipped:

```json
{ "v":1, "chars":"الفصل الأول…", "startMs":[0,80,140,…], "endMs":[80,140,210,…] }
```

Parallel `int[]` arrays instead of an array of objects cuts it ~5×; gzip another ~4×. Serve via presigned URL.

Derive **word-level** timings for the reader — character highlighting is almost never what a reading interface wants — reusing [TtsAlignmentMapper](../src/main/java/com/doova/ktab/features/tts/util/TtsAlignmentMapper.java) and [WordTiming](../src/main/java/com/doova/ktab/features/tts/dto/WordTiming.java), which already do this for the live WebSocket path. The client binary-searches the merged artifact by playback position. Postgres serves the index; R2 serves the payload.

### 3.5 What the Studio-mirror draft loses, and why

| table | verdict |
|---|---|
| `books` | `tbl_books` exists. A second one splits the truth for title/author/language, and `ocr_required`/`source_pdf_path` duplicate `col_ocr_status` and the `PDF_SOURCE` attachment. |
| `content_blocks` | Build-time intermediate only. |
| `content_nodes` | Same — and a second full copy of the book (~900k chars). |
| `chapter_voices` | Derivable; read it off the project row. |
| `chapter_snapshots` | Only the latest is ever served. Folded into `tbl_book_audio_chapters`. |
| `project_snapshots` | Whole-book archive is a download action, not durable state. |
| `pronunciation_dictionaries` + link table | JSONB on the project row. Reproducibility needs the `(id, version)` pair, not a normalised catalogue. |

The one worth arguing about is **blocks and nodes**. They earn a table only if something queries them at runtime, and nothing does: their entire purpose is computing character offsets, which are materialised onto pages and into the R2 timing artifact during projection, then never read again. Keeping the raw chapter JSON in R2 preserves every capability — including replaying a projection without re-calling the API — at a fraction of the storage.

If in-app text editing that pushes back to Studio is added later, add the tables then, rebuilt from the cached JSON. No data is lost by deferring.

### 3.6 Projection

```
studio chapter  ──> tbl_book_sections   (col_source = 'STUDIO', confidence 1.00, needsReview false)
studio blocks   ──> heading / paragraph markdown
studio nodes    ──> tbl_book_pages.col_markdown_clean
node offsets    ──> page char ranges, then the R2 timing artifact
```

Sections get `col_confidence = 1.00` and `col_needs_review = false` — not because we are confident in a guess, but because there was no guess.

**Synthetic pagination.** Studio has no page concept. Chunk each chapter's canonical text into page units **at block boundaries, never mid-block**:

- target ~1800 characters, flush at the first block boundary past target
- an `h1`/`h2` block always starts a new page
- `col_page_number` is a book-wide running sequence; `col_source_pdf_page = NULL`; `col_page_kind = BODY`; `col_ocr_status = COMPLETED`; `col_ocr_model = 'elevenlabs-studio'`

Deterministic for the same text, so re-projection is a no-op under the unique index in §3.3. Cutting at block boundaries also means no page ever splits a sentence — the page-join problem `HarmonizationTasklet` exists to solve simply does not arise on this route.

This keeps the entire existing reader API working untouched: `/text/range`, `/text/pages`, `/text/words` and in-book search all operate on pages and offsets. Nothing is currently bound to *PDF* page fidelity — the only `ReadingSession` in the codebase belongs to the `story` feature, not to books.

**The audio↔text index is a byproduct**, not a search:

```
char timing index → node   (offsets computed while concatenating — exact)
node              → page   (recorded while building the page — exact)
```

### 3.7 The sync engine

`features/studio/sync/StudioSyncService`, in two tiers. Conflating them is what makes naive implementations hammer both the API and the database.

**Tier 1 — status sync.** `GET /v1/studio/projects/{id}/chapters`. Frequent during conversion (backing off 3 s → 30 s). Touches only `col_conversion_progress`, `col_last_conversion_error`, `col_last_synced_at`. One `UPDATE ... WHERE col_external_chapter_id = ?` per chapter. Never touches content, never inserts.

**Tier 2 — content sync.** `GET /v1/studio/projects/{id}/chapters/{chapterId}`. Rare: once after project creation, on demand after an edit, once before final download. Per chapter, in one transaction:

1. Fetch, build canonical text, compute `sha256(canonicalText + structureFingerprint)`.
2. If it equals `col_content_hash` → touch `col_last_synced_at` and stop.
3. Otherwise re-project: upsert the section, upsert its pages, rewrite the R2 chapter JSON.
4. Write `col_content_hash`.

**Chapter-set reconciliation.** Left-join local against remote on `col_external_chapter_id`: remote-only → insert; both → update; local-only → set `col_deleted_at`, log, increment a counter. A chapter disappearing from Studio should page someone — it means an out-of-band edit or the wrong project.

**Concurrency.** Take `SELECT ... FOR UPDATE` on the `tbl_studio_projects` row (or a Postgres advisory lock on `col_book_id`) at the top of each sync. Batch retries and the scheduled reconciler *will* overlap otherwise.

### 3.8 Two writers, one set of tables

**Discriminate the source.** `tbl_book_sections.col_source = 'STUDIO'`; `tbl_book_pages.col_ocr_model = 'elevenlabs-studio'`. That answers "which books came from which pipeline" without a join.

**One integration test both pipelines run**, asserting the Layer 3 contract:

- `col_page_number` contiguous from 1, no gaps
- `col_markdown_clean` non-null on every page
- every page's `col_section_id` resolvable
- section `col_start_page`/`col_end_page` covering the book without gaps or overlaps
- `tbl_books.col_page_count` equal to the page count

That test *is* the contract — cheaper and more reliable than a `BookContentWriter` interface the two implementations would drift from anyway.

### 3.9 Projection quality gate

The residual risk is not "did we match correctly" but "did Studio parse sensibly" — checkable without matching anything:

- chapter count within `[2, 500]`
- no chapter exceeding ~15% of total characters (catches the one-giant-chapter failure)
- no empty chapters
- total characters within ~20% of the PDF's own text-layer character **count** (a count, not a content comparison)

Fail any → purge (§2.2), persist the reason, **reroute to `ocrJob`**. A route-level fallback, not a reconciliation.

This is also why the two pipelines need **separate quality gates**. [QualityGateTasklet](../src/main/java/com/doova/ktab/features/ocr/quality/QualityGateTasklet.java) asserts on `col_image_quality` and `maxPoorRatio`, which Studio books never populate — a shared gate would either pass vacuously or fail on a null.

---

## Phase 4 — Audiobook conversion

Lives in `features/studio/audiobook/`. Runs for books from **either** pipeline.

### 4.1 Push path for OCR'd books

Create one Studio chapter per `BookSection` via the chapters API, with our own text from `col_markdown_clean`; markdown headings map to `h1/h2/h3`, paragraphs to `p`. Write `col_external_chapter_id` onto that section in the same transaction, `col_origin = 'KTAB_PUSHED'`. Mechanical, and it makes audiobooks work for scanned books too.

### 4.2 Execution model

**Never run `processBook()` on a request thread.** A 30-minute blocking call with a polling loop will be killed by the load balancer, will not survive a deploy, and cannot be observed. Model it as a Spring Batch job:

| step | action |
|---|---|
| `estimateStep` | count characters, compute credit cost, check quota, hard-fail over `studio.max-chars-per-book` |
| `createProjectStep` | persist `col_external_project_id` and advance to `CREATED` **in one committed transaction, before anything else** |
| `syncContentStep` | Tier 2 content sync (digital) or `pushChaptersStep` (OCR) |
| `convertStep` | fire `/convert` once, `lifecycle = CONVERTING` |
| `pollStep` | tasklet returning `RepeatStatus.CONTINUABLE` with backoff — **not** a `while(true)` loop, so Batch checkpoints between polls and a restart resumes instead of reconverting |
| `downloadStep` | chunk-oriented, one chapter per item, parallel across `studioTaskExecutor` |
| `timingIndexStep` | build and upload the merged timing + offset artifacts |
| `finalizeStep` | verify every chapter present, size and sha256 match, durations sane → write `tbl_book_audio_chapters`, set `col_has_audio = true` |
| `cleanupStep` | delete the Studio project, set `col_project_deleted_at`, `lifecycle = DELETED` — **only after `finalizeStep` verifies** |

Deleting on a partial download means paying for the conversion twice. A crash between project creation and cleanup still leaks a paid project, so the reconciler in §4.5 is authoritative rather than relying on step ordering.

### 4.3 Streaming MP3

Stream ElevenLabs → R2 directly via S3 multipart upload, not to local disk. Containers have small ephemeral disks, and a 300-page book produces 300–600 MB of audio; a temp file per concurrent job fills the volume. Use an 8 MB part buffer, compute sha256 incrementally over the stream, and **abort the multipart upload in a `finally`** so orphaned parts don't accrue — plus an R2 lifecycle rule to reap them regardless.

### 4.4 Concurrency, retry, cost

- **Global concurrency cap**, not per-job. [DynamicConcurrencyGate](../src/main/java/com/doova/ktab/features/ocr/quota/DynamicConcurrencyGate.java) already implements this pattern for Gemini; the Studio pipeline needs its own equivalent rather than a new ad-hoc semaphore. (Copy the pattern — do not import the class; see §2.3.) Default 2 concurrent books.
- **Retry**: exponential backoff with full jitter on network errors, 429 and 5xx; honour `Retry-After`; fail fast on any other 4xx. Resilience4j is already a dependency and [GeminiOcrProcessor](../src/main/java/com/doova/ktab/features/ocr/batch/GeminiOcrProcessor.java) wires a circuit breaker with configurable thresholds — mirror that setup.
- **Cost guard** — the piece most likely to hurt. TTS on a full book is real money, and a retry storm or a stuck poll that re-converts silently multiplies it. Require an explicit admin/publisher trigger (never auto-fire on publish in v1); enforce a per-book character ceiling and a per-organisation monthly credit budget checked in `estimateStep`; maintain a `credits_used` counter to alert on; provide `studio.dry-run` that does everything except `/convert`.
- **API key from environment only.** Never in `application.yml`. `spring-dotenv` is on the classpath — confirm `elevenlabs.api-key` is not resolving from a committed file.

### 4.5 Orphan reconciliation

`finally` does not run on SIGKILL, OOM-kill or node eviction, so this is a requirement, not an edge case.

Hourly `@Scheduled`: list Studio projects via the API, left-join `tbl_studio_projects`, delete any project unknown locally, or attached to a terminal-lifecycle row with `col_project_deleted_at IS NULL` and age > 2 h. Emit `studio.orphans.reclaimed` and alert if it is ever non-zero — a steady trickle means the happy path is broken.

---

## Phase 5 — Observability, testing, rollout

### Metrics

Separate namespaces, matching the pipeline separation.

```
ingestion.classification.result{type}      ingestion.classification.duration
ingestion.classification.overridden{from,to}   ← accuracy signal in production
ingestion.route.selected{route}
ingestion.cost.pages_ocred  vs  ingestion.cost.pages_via_studio

studio.sync.content{result}    studio.chapters.vanished
studio.projection.gate{result}
studio.jobs{status}            studio.chars_converted
studio.orphans.reclaimed
```

### Testing

**The classifier fixture corpus is the deliverable that matters most.** ~20 real PDFs covering: born-digital Arabic, Word export, LaTeX, clean scan, skewed scan, Tesseract-hybrid, ABBYY-hybrid, **Form-XObject-wrapped scan** (§1.1), mixed digital + scanned plates, rotated pages, encrypted, two-column RTL, broken `ToUnicode`. Assert expected `PdfType` per file. Everything else in Phase 1 is tuning against this corpus.

- The Layer 3 contract test (§3.8), run against both pipelines.
- Projection idempotency: sync the same chapter twice, assert zero row churn and identical `col_id`s.
- Purge-before-write: run Studio, force a gate failure, reroute to OCR, assert contiguous page numbers.
- ArchUnit: no class in `features.studio` imports `features.ocr`.
- WireMock for ElevenLabs: happy path, 429-then-success, chapter-level conversion error, timeout, mid-download disconnect, chapter reorder (exercises the deferred constraint), chapter vanished.
- Crash path: kill the job after `createProjectStep`, assert the reconciler reclaims the project.

### Rollout

1. **Shadow mode** (`ingestion.classification.shadowMode=true`): classify every incoming book, persist type and evidence, route everything to OCR. Run two weeks.
2. Compare shadow classifications against real outcomes; tune thresholds against the fixture set; bump `col_classifier_version` and re-classify history if needed.
3. Enable the `STUDIO` route for `DIGITAL` only, behind a per-organisation flag, starting with one internal org.
4. Widen. Revisit `HYBRID_OCR` routing with data in hand.
5. Audiobook ships behind its own flag: manual trigger, dry-run first, one real book end-to-end before enabling for anyone.

---

## Consequences accepted

**1. ElevenLabs becomes a hard dependency for ingesting digital books.** Previously an outage would only have blocked audio; now it stalls digital ingestion. Mitigations: the projection gate's OCR fallback doubles as an outage fallback, and the raw chapter JSON cached in R2 lets a projection be replayed without re-calling the API.

Note the digital pipeline reaches text **without** `/convert` — project creation and chapter retrieval do not consume TTS credits, only conversion does. A digital book nobody wants audio for can use Studio purely as a parser: create, sync, project, delete.

**2. Re-uploading a PDF into a new Studio project yields new `chapter_id`s.** The upserts in §3.3 key on those ids, so a fresh project regenerates sections and pages with new row ids. Policy: never re-upload into an existing book. Create the new project, project into a staging state, verify the gate, then swap — keeping the old project `ARCHIVED` until the new audio is verified.

**3. Digital books lose PDF page fidelity.** Their pages are synthetic (§3.6). Acceptable today because no reading-position model is bound to PDF pages; it would need revisiting if citation-by-printed-page ever becomes a requirement.

**4. Two writers into `tbl_book_pages` / `tbl_book_sections`.** Mitigated by mandatory purge (§2.2), the shared contract test (§3.8) and source discriminators — but it is a real invariant that now depends on discipline in two places rather than one.

---

## Effort

| phase | estimate |
|---|---|
| 0 — enums, migrations, properties split, `ObjectStorageService` extraction | 2 d |
| 1 — classifier + fixture corpus | 3–4 d (over half is fixtures and tuning) |
| 2 — router, purger, escape hatches, ArchUnit | 2 d |
| 3 — three tables, sync engine, projector, synthetic pagination | 6 d |
| 4 — conversion job, streaming download, push path, reconciler | 5 d |
| 5 — metrics, tests, shadow-mode rollout | 3 d |
