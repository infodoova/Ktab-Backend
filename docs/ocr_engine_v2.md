# OCR Engine → Structure-Aware Book Processor (v2)

## Problem Statement

The current OCR pipeline converts PDF pages to images, calls Gemini page-by-page, and saves flat `BookPage` rows. Every page ends up as anonymous text with a page number: no book structure, no chapter grouping, no appendix labeling, no separation of body text from running headers, page numbers and footnotes.

Downstream consumers (reader API, TTS) need:
- A **section tree** (front matter → parts/chapters/sub-sections → appendix → bibliography/index), with each section's start located precisely enough to slice content by heading, not just by page.
- **Clean body text** per page, with headers/footers/page numbers removed and footnotes held separately (so TTS doesn't read them inline).
- **Clean page-boundary joins** (paragraphs continuing across pages).
- **One image per book page**, upright and at a resolution that preserves tashkeel and footnote text — scanned PDFs frequently contain two-page spreads, rotated pages and poor-quality scans.
- **Flags** for pages that need human review.

> [!NOTE]
> `OcrPromptServiceImpl` already has `getTocPrompt()`, `getOcrPrompt(toc)` and `getHarmonizePrompt(toc, startingContext, rawText)`. v2 reuses the TOC and harmonize prompts (revised to return structured output) and replaces the per-page OCR prompt with one that returns page structure as well as text.

---

## What changed from v1, and why

| v1 assumption | Why it breaks | v2 approach |
|---|---|---|
| TOC is in the first 15 pages | In many Arabic books the فهرس is at the **end** of the book. | Classify every page during OCR; the TOC step uses the pages that were actually detected as `TOC`, wherever they are. |
| TOC page numbers = PDF page indices | Printed numbers are offset by covers, unnumbered front matter, abjad-lettered front matter (أ، ب، ج), inserted plates and missing scans. They may also be written in Arabic-Indic digits (٤٥). | OCR extracts each page's **printed page label**; a deterministic resolver builds a printed→PDF mapping (piecewise, outlier-tolerant) and verifies every TOC entry against headings detected on the target page. |
| Resolve section **before** OCR and inject TOC into the prompt | Couples OCR to structure (a wrong TOC corrupts every page), bloats every prompt, and invites the model to "insert" TOC headings into page text. | OCR is **stateless and structure-agnostic**. Structure is resolved **after** OCR, in plain Java, and can be re-run without re-OCR. |
| `previousPageTail` via `AtomicReference` | In a multi-threaded / parallel chunk step, pages finish out of order, so the reference holds the tail of whatever page finished last, not page N-1. Silent wrong-context bug. | Harmonization is a **separate step** that reads page N-1 and N+1 from the DB. Deterministic and parallel-safe. |
| `BookStructure` stored in `JobExecutionContext` | Not queryable, not reusable across runs, not correctable by a human, and bloats Spring Batch metadata tables. | Structure persisted in `tbl_book_sections`. |
| `section_type` / `chapter_number` / `section_title` on each page | A page can contain the end of one section and the start of the next; structure is hierarchical; "chapter number" is ambiguous in Arabic books (الكتاب / الباب / الفصل / المبحث / المطلب). | Sections table with `parent_id`, `level`, original division label, and a heading anchor. Pages reference the section they start in. |
| "Page type" and "section type" are one enum | A page's visual kind (blank, cover, TOC) is different from its semantic section (an appendix page is still a body page). | Two enums: `PageKind` and `SectionType`. |
| Harmonization rewrites the page text | LLM cleanup can silently change words, "correct" classical Arabic, or add diacritics. Irreversible if it overwrites raw OCR. | Raw OCR is immutable; cleaned text goes to a separate column, guarded by a similarity check. |
| TOC always extracted by vision | Wastes money when the PDF has bookmarks or a usable text layer. | Source cascade: PDF outline → validated text layer → vision on detected TOC pages → headings-only fallback. |
| Parse free-text JSON from Gemini | Fragile. | Gemini structured output (`responseMimeType=application/json` + `responseSchema`). |
| One PDF page = one book page, rendered as-is | Scans often hold two-page spreads, sideways/upside-down pages, dark borders and faded print. Spreads break page numbering, offset resolution and stitching; rotated or low-resolution pages silently produce weak text. | Dedicated **image preparation** in decomposition: spread detection and RTL-aware splitting, orientation correction, controlled render resolution, border cropping, image-quality flags. `page_number` becomes the book-page index; the source PDF page is kept separately. |

On the "agent" framing: the right design here is a **deterministic pipeline with LLM calls at fixed points**, not a free-running agent loop. That is what v1 already was in practice, and v2 keeps it that way. It is cheaper, restartable and testable.

---

## Resolved Decisions (v1 open questions)

1. **Section type column?** Yes, but as a `tbl_book_sections` table plus `col_section_id` on pages, and a separate `col_page_kind` on pages.
2. **`chapter_number` / `section_title`?** Replaced by a hierarchical section model: `level`, `parent_id`, `division_label` (the original word, e.g. `الفصل`), optional parsed `ordinal`, `title`.
3. **Harmonization pass?** Yes, as its own optional step (`ktab.ocr.harmonize.enabled=false` by default), text-only (cheaper than the vision call, and can use a smaller model), with guardrails.
4. **TOC source?** Cascade: PDF outline (free) → PDFBox text layer, only if it passes an Arabic sanity check (scanned PDFs often carry a broken OCR layer with reversed or unshaped text) → vision on detected TOC pages → headings-only fallback.

---

## Architecture

### Current Flow
```
PDF → Decompose pages → OCR each page → Save BookPage (text only)
```

### v2 Flow
```
[Step 1] Decompose, Prepare Images + Probe (sequential per PDF)
         render at controlled DPI → crop dark borders → detect & split spreads
         (right half first for RTL) → pre-check suspicious orientation
         → image quality metrics → upload to S3 → page manifest rows (PENDING)
         read PDF outline; test text layer quality
    ↓
[Step 2] Page OCR (parallel, stateless, one vision call per page image)
         → structured JSON per page: kind, orientation, image quality,
           printed label, running header, headings, body markdown,
           footnotes, boundary flags
         → rotate + retry if orientation ≠ 0; retry POOR pages at higher DPI
         → persist raw
    ↓
[Step 3] TOC Extraction (one call, only on pages where kind = TOC)
         → entries: title, level, division label, printed page label, section type
         (skipped if the PDF outline already gives a usable structure)
    ↓
[Step 4] Structure Resolution (pure Java, no LLM, re-runnable)
         printed→PDF page mapping → align TOC to detected headings
         → build section tree → assign pages → confidence + review flags
    ↓
[Step 5] Stitch & Harmonize (optional; text-only; reads neighbours from DB)
         → markdown_clean with similarity guard
    ↓
[Step 6] Quality Gate
         → per-page flags, per-book structure status
```

Steps 4–6 can be triggered independently (`/restructure`, `/harmonize`) after a human corrects the structure, without paying for OCR again.

---

## Component 1 — Database Schema

#### [NEW] `V5__book_structure.sql`
```sql
-- Sections: the book's structure tree
CREATE TABLE IF NOT EXISTS tbl_book_sections (
    col_id                 BIGSERIAL PRIMARY KEY,
    col_book_id            BIGINT       NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_parent_id          BIGINT       REFERENCES tbl_book_sections (col_id) ON DELETE CASCADE,
    col_section_type       VARCHAR(30)  NOT NULL,          -- SectionType enum
    col_level              SMALLINT     NOT NULL,          -- 0 = top level
    col_sort_order         INTEGER      NOT NULL,
    col_division_label     VARCHAR(50),                    -- e.g. 'الباب', 'الفصل', 'المبحث'
    col_ordinal            INTEGER,                        -- parsed from 'الفصل الثالث' → 3, if any
    col_title              VARCHAR(1000) NOT NULL,
    col_title_normalized   VARCHAR(1000) NOT NULL,         -- for fuzzy matching
    col_printed_start_label VARCHAR(20),                   -- as printed in TOC: '45', '٤٥', 'ج'
    col_start_page         INTEGER,                        -- book page index (col_page_number)
    col_end_page           INTEGER,
    col_start_anchor       VARCHAR(1000),                  -- heading text as it appears in page markdown
    col_source             VARCHAR(20)  NOT NULL,          -- PDF_OUTLINE | TEXT_LAYER | TOC_VISION | HEADINGS | MANUAL
    col_confidence         NUMERIC(3,2) NOT NULL DEFAULT 0,
    col_needs_review       BOOLEAN      NOT NULL DEFAULT FALSE,
    col_created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    col_updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_book_sections_book ON tbl_book_sections (col_book_id, col_sort_order);

-- Pages: image provenance + per-page structure signals + clean text + provenance
-- NOTE: col_page_number becomes the BOOK page index (1..N after spread splitting),
--       no longer the PDF page index. The PDF page is kept in col_source_pdf_page.
ALTER TABLE tbl_book_pages
    ADD COLUMN IF NOT EXISTS col_source_pdf_page      INTEGER,
    ADD COLUMN IF NOT EXISTS col_spread_side          VARCHAR(10) NOT NULL DEFAULT 'NONE', -- NONE|RIGHT|LEFT
    ADD COLUMN IF NOT EXISTS col_rotation_degrees     SMALLINT    NOT NULL DEFAULT 0,      -- 0|90|180|270 applied before OCR
    ADD COLUMN IF NOT EXISTS col_render_dpi           SMALLINT,
    ADD COLUMN IF NOT EXISTS col_image_width          INTEGER,
    ADD COLUMN IF NOT EXISTS col_image_height         INTEGER,
    ADD COLUMN IF NOT EXISTS col_image_quality        VARCHAR(10),                         -- GOOD|FAIR|POOR
    ADD COLUMN IF NOT EXISTS col_image_metrics        JSONB,  -- {contrast, inkDensity, borderCropPx, ...}
    ADD COLUMN IF NOT EXISTS col_page_kind            VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS col_printed_page_label   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS col_running_header       VARCHAR(500),
    ADD COLUMN IF NOT EXISTS col_headings             JSONB,        -- [{text, levelHint}]
    ADD COLUMN IF NOT EXISTS col_footnotes_markdown   TEXT,
    ADD COLUMN IF NOT EXISTS col_starts_mid_sentence  BOOLEAN,
    ADD COLUMN IF NOT EXISTS col_ends_mid_sentence    BOOLEAN,
    ADD COLUMN IF NOT EXISTS col_markdown_clean       TEXT,         -- harmonized; raw markdown column stays untouched
    ADD COLUMN IF NOT EXISTS col_section_id           BIGINT REFERENCES tbl_book_sections (col_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS col_ocr_status           VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING|DONE|FAILED|FLAGGED
    ADD COLUMN IF NOT EXISTS col_quality_flags        JSONB,        -- ["RECITATION","TRUNCATED","REPETITION",...]
    ADD COLUMN IF NOT EXISTS col_ocr_model            VARCHAR(100),
    ADD COLUMN IF NOT EXISTS col_prompt_version       VARCHAR(20);

-- Backfill for existing rows (produced before spread splitting)
UPDATE tbl_book_pages SET col_source_pdf_page = col_page_number WHERE col_source_pdf_page IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_book_pages_book_page ON tbl_book_pages (col_book_id, col_page_number);
CREATE INDEX IF NOT EXISTS idx_book_pages_source_pdf ON tbl_book_pages (col_book_id, col_source_pdf_page, col_spread_side);
CREATE INDEX IF NOT EXISTS idx_book_pages_section ON tbl_book_pages (col_book_id, col_section_id);

-- Book-level structure + scan status
ALTER TABLE tbl_books
    ADD COLUMN IF NOT EXISTS col_reading_direction    VARCHAR(3)  NOT NULL DEFAULT 'RTL', -- RTL|LTR (spread split order)
    ADD COLUMN IF NOT EXISTS col_pagination_mode      VARCHAR(10),                         -- PRINTED|PARTIAL|NONE
    ADD COLUMN IF NOT EXISTS col_structure_status     VARCHAR(20) DEFAULT 'NONE', -- NONE|RESOLVED|NEEDS_REVIEW|MANUAL
    ADD COLUMN IF NOT EXISTS col_structure_source     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS col_toc_raw              JSONB;        -- raw extracted TOC, for audit/debug
```
> Verify table/column names (`tbl_books.col_id`, the existing markdown column) against the current schema before applying. The unique index makes reruns an upsert rather than duplicate rows; check existing data for duplicates first.
>
> **Breaking semantic change:** after this migration, `col_page_number` is the book-page index. Any API client or query that treats it as the PDF page number must switch to `col_source_pdf_page`. Books already processed keep a 1:1 mapping until they are re-decomposed.

---

## Component 2 — Decomposition & Image Preparation (Step 1)

Scanned books fail here more than anywhere else. Decomposition changes from "render each PDF page and upload" to a preparation pipeline that produces **one upright, correctly-ordered image per book page**, plus a manifest the OCR step reads from.

#### [MODIFY] Decomposition step → `PageImagePreparationTasklet`
Per PDF page, **sequentially** (PDFBox `PDDocument` is not thread-safe; parallelism belongs to the OCR step):
1. Render (`PageRenderer`).
2. Crop dark scan borders (`BorderCropper`).
3. Detect and split spreads (`SpreadDetector` / `SpreadSplitter`).
4. Pre-check orientation for outliers (`OrientationPreChecker`).
5. Compute image quality metrics (`ImageQualityAnalyzer`).
6. Encode, upload to S3, write a `tbl_book_pages` row with `col_ocr_status = PENDING`.
7. Release the image before moving to the next page.

Book page numbering is assigned here in reading order: `col_page_number` increments per output image, `col_source_pdf_page` and `col_spread_side` record where it came from.

#### [MODIFY] OCR step reader
Reads the page manifest (`JdbcPagingItemReader` over `tbl_book_pages WHERE col_book_id = ? AND col_ocr_status IN ('PENDING', …)`) instead of iterating PDF page numbers. This also gives `retry-flagged` for free.

---

#### 2.1 Render resolution — [NEW] `PageRenderer`
- Render with `PDFRenderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB)` at `ktab.ocr.image.dpi` (default **300**). Lower DPI loses tashkeel and small footnote text; higher DPI adds image tokens with no quality gain.
- **Clamp by pixels, not only DPI.** Scanned PDFs often have odd `MediaBox` sizes, so a fixed DPI can produce tiny or enormous images. After rendering, bound the longest side between `ktab.ocr.image.min-long-side-px` (default 2000) and `ktab.ocr.image.max-long-side-px` (default 3500); re-render at an adjusted DPI if outside the range.
- **Single-image pages:** when a page is one embedded raster image covering the page, optionally extract it directly (`PDImageXObject`) instead of re-rasterizing, to avoid resampling. Controlled by `ktab.ocr.image.prefer-embedded=true`; still subject to the pixel clamp.
- **Memory:** open large PDFs with `MemoryUsageSetting.setupTempFileOnly()`; render, process, encode and upload one page at a time; never hold the whole book's images in memory.
- **Encoding:** JPEG quality `ktab.ocr.image.jpeg-quality` (default 90), grayscale kept as RGB/gray, **no binarization** (thresholding destroys tashkeel and faint text; Gemini reads grayscale originals better).
- Record `col_render_dpi`, `col_image_width`, `col_image_height`.
- Before fixing defaults, measure token usage and CER at 200/300/400 DPI on the golden set.

#### 2.2 Dark borders — [NEW] `BorderCropper`
- Scan rows/columns inward from each edge; trim bands whose mean luminance is below a threshold (black scanner bed, shadow).
- Keep a safety margin (e.g. 1% of the dimension) so page numbers and marginal headers near edges are not cut.
- Never crop more than `ktab.ocr.image.max-border-crop-ratio` (default 0.15) per side; if more would be removed, skip cropping and add metric `borderCropSkipped`.
- Runs **before** spread detection so the aspect ratio reflects the paper, not the scanner bed.

#### 2.3 Two-page spreads — [NEW] `SpreadDetector` + `SpreadSplitter`
**Detection (per page, since a book can mix single covers and inner spreads):**
1. **Candidate:** width / height > `ktab.ocr.spread.aspect-ratio-threshold` (default **1.2**).
2. **Confirm with a gutter:** compute the vertical projection profile (column ink density) in the central band (40%–60% of width). A gutter shows as a low-ink valley or a dark shadow line. Candidate + gutter → spread.
3. **Candidate without a gutter** → not split yet. This is either a landscape single page (table, map) or a page rotated 90°; hand it to the orientation pre-check (2.4).
4. **Book-level consistency:** if most pages in the book are spreads, lower the confidence bar for borderline pages; if a page's aspect ratio is ~2× the book's median single-page ratio, favour spread.

**Splitting:**
- Split at the detected gutter x-coordinate, not the geometric middle (scans are rarely centred).
- **Order by reading direction:** for `col_reading_direction = RTL` (default for Arabic), the **right half is the earlier page** and gets the lower `col_page_number`, `col_spread_side = RIGHT`; the left half follows, `LEFT`. For `LTR` books, reversed.
- Reading direction is set per book (from the book's language/metadata, overridable) — never inferred per page.
- Each half then goes through border cropping, orientation and quality checks individually.

**Post-OCR verification (safety net):**
- If OCR on a single (unsplit) image returns **two printed page labels** or the model reports `pageKind = SPREAD`, flag `UNSPLIT_SPREAD`, split at the centre band, re-OCR both halves, and renumber pages for that book (`PageRenumberer`, a transactional shift of later `col_page_number` values).
- If two halves of a split both come back `BLANK`/near-empty, or one half has text cut mid-line at the split edge, flag `BAD_SPLIT` for review.

#### 2.4 Orientation — [NEW] `OrientationPreChecker` + OCR-reported orientation
Two layers, so the common case costs nothing extra:
1. **Pre-check (only suspicious pages):** pages whose orientation (landscape vs. portrait) disagrees with the book's median and that were not confirmed as spreads. Send a **low-resolution thumbnail** (e.g. long side 768 px) to a cheap model call returning `{ "rotationDegrees": 0|90|180|270, "isSpread": bool }`. Rotate before upload; if `isSpread`, route back to 2.3.
2. **In-OCR check (every page, no extra call):** the OCR response schema includes `orientation` (0/90/180/270) and allows `pageKind = UNREADABLE`. If `orientation ≠ 0` or the page is `UNREADABLE` with a hint of rotation, rotate the stored image, re-upload, re-OCR once. Catches upside-down pages (180°), which aspect ratio can't detect.
3. Record `col_rotation_degrees`; flag `ROTATED` for traceability. Still unreadable after one retry → `FLAGGED` with `UNREADABLE`.

Skew (a few degrees of tilt) is not corrected by default; Gemini handles it. Add deskewing only if the golden set shows CER problems.

#### 2.5 Image quality — [NEW] `ImageQualityAnalyzer` + OCR-reported quality
**Pre-OCR metrics (cheap, deterministic), stored in `col_image_metrics`:**
- Contrast (luminance standard deviation).
- Ink density (share of dark pixels after an adaptive threshold used for measurement only, never for the uploaded image).
- Show-through / bleed-through estimate (mid-grey mass relative to dark text mass).
- Border crop applied, pixel dimensions.

**OCR-reported (added to the OCR response schema):**
```json
"imageQuality": "GOOD | FAIR | POOR",
"hasStamps": false,
"hasHandwriting": true,
"illegibleSegments": 2
```

**Rules:**
- `col_image_quality` = worse of metric-derived and model-reported quality.
- `POOR` → retry once at `ktab.ocr.image.retry-dpi` (default 400); if still `POOR`, keep the text but flag `LOW_IMAGE_QUALITY`.
- `illegibleSegments > 0` → flag `ILLEGIBLE_TEXT`.
- `hasHandwriting` / `hasStamps` → informational flags (`HANDWRITING_PRESENT`, `STAMP_PRESENT`) so reviewers know marginalia were excluded.
- Book-level: if > `ktab.ocr.quality.max-poor-ratio` (default 10%) of pages are `POOR`, mark the book `NEEDS_REVIEW` and surface it — likely a rescan candidate.

**OCR prompt additions (prompt version `v2`):**
- Transcribe **printed text only**. Ignore stamps, library/ownership seals, handwritten marginalia, underlining, notes and signatures.
- Do not guess unreadable words. Replace each illegible fragment with the sentinel `[[ILLEGIBLE]]` and count them in `illegibleSegments`. Exports and TTS drop or handle the sentinel explicitly; it is never silently rendered.
- Report `orientation`, `imageQuality`, `hasStamps`, `hasHandwriting` honestly, even when text was extracted.

#### 2.6 Books with no printed page numbers
Common in old printings and manuscript-style books. Handled in structure resolution (Component 7), but decided here and recorded at book level:
- After OCR, `PaginationModeDetector` computes the share of `BODY` pages with a parseable numeric label:
  - ≥ 70% → `PRINTED` (normal offset resolution),
  - 20–70% → `PARTIAL` (offset resolution where labels exist, heading alignment elsewhere),
  - < 20% → `NONE` (heading alignment only).
- Stored in `tbl_books.col_pagination_mode`.
- In `NONE` mode, section confidence is capped at `ktab.ocr.structure.no-pagination-confidence-cap` (default 0.75), so these books default to `NEEDS_REVIEW` unless every TOC entry matched a heading exactly. Running-header changes get more weight as boundary evidence.
- TOCs that list titles **without** page numbers use the same path.

#### Page-flag vocabulary (additions)
`UNSPLIT_SPREAD`, `BAD_SPLIT`, `ROTATED`, `UNREADABLE`, `LOW_IMAGE_QUALITY`, `ILLEGIBLE_TEXT`, `HANDWRITING_PRESENT`, `STAMP_PRESENT`, `BORDER_CROP_SKIPPED`

---

## Component 3 — Domain Model

#### [NEW] `com.doova.ktab.enums.book.PageKind`
```java
public enum PageKind {
    COVER, TITLE_PAGE, COPYRIGHT, BLANK, TOC, BODY, INDEX, IMAGE_ONLY,
    SPREAD,       // model saw two pages in one image → triggers split + re-OCR
    UNREADABLE,   // rotated / illegible → triggers rotation retry
    OTHER, UNKNOWN
}
```

#### [NEW] `com.doova.ktab.enums.book.SectionType`
```java
public enum SectionType {
    FRONT_MATTER, DEDICATION, FOREWORD, PREFACE, INTRODUCTION,
    PART, CHAPTER, SUBSECTION,
    CONCLUSION, APPENDIX, BIBLIOGRAPHY, INDEX, GLOSSARY,
    OTHER
}
```

#### [NEW] `com.doova.ktab.enums.book.StructureSource`
`PDF_OUTLINE, TEXT_LAYER, TOC_VISION, HEADINGS, MANUAL`

#### [NEW] `SpreadSide` (`NONE, RIGHT, LEFT`), `ReadingDirection` (`RTL, LTR`), `ImageQuality` (`GOOD, FAIR, POOR`), `PaginationMode` (`PRINTED, PARTIAL, NONE`)

#### [NEW] `com.doova.ktab.model.book.BookSection` (JPA entity for `tbl_book_sections`)

#### [MODIFY] `BookPage.java`
Add the fields from the migration. `col_markdown` (raw) is never overwritten after OCR.

---

## Component 4 — Arabic Text Utilities

#### [NEW] `com.doova.ktab.features.ocr.text.ArabicTextNormalizer`
Used by matching, never for stored display text.
- Strip tashkeel (U+064B–U+0652, U+0670) and tatweel (U+0640).
- Normalize `أ إ آ ٱ → ا`, `ى → ي`, `ة → ه`, `ؤ → و`, `ئ → ي`.
- Convert Arabic-Indic (٠–٩) and Extended/Persian (۰–۹) digits to ASCII.
- Collapse whitespace, strip punctuation and leader dots (`.....`).

#### [NEW] `com.doova.ktab.features.ocr.text.PageLabelParser`
- Parses printed labels into `Optional<Integer>` for arabic/ASCII digits.
- Recognizes abjad front-matter lettering (أ ب ج د ه و ز ح ط ي …) and roman numerals as **front-matter labels** (not mapped to body page numbers).

#### [NEW] `com.doova.ktab.features.ocr.text.SectionClassifier`
Keyword fallback when the LLM doesn't return a type (LLM classification from the TOC call is primary):

| Keywords (normalized) | SectionType |
|---|---|
| اهداء | DEDICATION |
| تقديم، كلمه | FOREWORD |
| مقدمه، تمهيد | INTRODUCTION |
| الباب، الكتاب، القسم | PART |
| الفصل | CHAPTER |
| المبحث، المطلب، الفرع | SUBSECTION |
| خاتمه | CONCLUSION |
| ملحق، الملاحق | APPENDIX |
| المراجع، المصادر، ثبت المصادر | BIBLIOGRAPHY |
| فهرس الاعلام، فهرس الايات، فهرس الاحاديث، فهرس الاماكن | INDEX |
| فهرس الموضوعات، فهرس المحتويات | (TOC itself; not a section) |

> `فهرس` alone is ambiguous (TOC vs index). Decide from context: position, and whether the entries are topics with page numbers or names/verses with page lists.

---

## Component 5 — Page OCR (Step 2)

#### [MODIFY] `GeminiOcrProcessor.java`
- **Stateless.** No TOC, no previous-page context, no shared mutable state.
- One vision call per page with structured output schema:

```json
{
  "pageKind": "BODY",
  "orientation": 0,
  "imageQuality": "FAIR",
  "hasStamps": false,
  "hasHandwriting": true,
  "illegibleSegments": 0,
  "printedPageLabel": "٤٥",
  "runningHeader": "الفصل الثالث: ...",
  "headings": [{ "text": "المبحث الأول: ...", "levelHint": 2 }],
  "bodyMarkdown": "...",
  "footnotesMarkdown": "...",
  "startsMidSentence": true,
  "endsMidSentence": false
}
```

- Prompt rules (new prompt version `v2`): transcribe exactly; do not correct spelling or grammar; do not add or remove diacritics; exclude running header, page number and footnotes from `bodyMarkdown`; preserve Qur'anic verse brackets ﴿ ﴾ and quotation marks as printed; transcribe printed text only (ignore stamps, seals, handwritten notes); mark illegible fragments `[[ILLEGIBLE]]`. Full rules in Component 2.5.
- Inspect `finishReason` and response, set `col_quality_flags`:
  - `RECITATION` → retry once with adjusted settings/fallback model; if still blocked, mark `FLAGGED`.
  - `MAX_TOKENS` → `TRUNCATED`, retry with higher output limit.
  - Repetition loop (same line repeated ≥ N times, or low unique-n-gram ratio) → `REPETITION`, retry.
  - Empty body on a page classified `BODY` → `EMPTY_BODY`.
  - `orientation ≠ 0` / `UNREADABLE` → rotate + re-OCR once (Component 2.4).
  - `SPREAD` or two printed labels → split + re-OCR (Component 2.3).
  - `imageQuality = POOR` → re-render at retry DPI + re-OCR once (Component 2.5).
- Record `col_ocr_model`, `col_prompt_version`.

#### [MODIFY] `OcrResult.java`
```java
public record OcrResult(
    Long bookId, int pageNumber, String s3Key,
    int sourcePdfPage, SpreadSide spreadSide, int rotationDegrees,
    PageKind pageKind, ImageQuality imageQuality, int illegibleSegments,
    String printedPageLabel, String runningHeader,
    List<DetectedHeading> headings,
    String bodyMarkdown, String footnotesMarkdown,
    Boolean startsMidSentence, Boolean endsMidSentence,
    int wordCount, OcrStatus status, List<String> qualityFlags,
    String model, String promptVersion
) {}

public record DetectedHeading(String text, int levelHint) {}
```

#### [MODIFY] `BookSectionWriter.java` → rename to `BookPageWriter`
Upsert on `(book_id, page_number)`. (The current name will clash conceptually with the new `BookSection` entity.)

#### Throughput & resilience
- Multi-threaded step (`TaskExecutor`, `ktab.ocr.parallelism`) — safe now that the processor is stateless.
- Client-side rate limiter (e.g. Resilience4j `RateLimiter`) sized to the Gemini quota.
- Retry with exponential backoff on 429/5xx; a skip policy marks the page `FAILED` instead of failing the whole job.
- For non-interactive backfills of the library, price out Gemini's batch mode against the online API.

---

## Component 6 — TOC Extraction (Step 3)

#### [NEW] `com.doova.ktab.features.ocr.structure.TocSource` (strategy interface)
```java
public interface TocSource {
    StructureSource source();
    Optional<RawToc> extract(Long bookId);
}
```

Implementations, tried in order:
1. **`PdfOutlineTocSource`** — PDFBox `PDDocumentOutline`. Free; present in many digital-born and some archive-scanned PDFs. Bookmark destinations give PDF page indices directly (no offset resolution needed).
2. **`TextLayerTocSource`** — PDFBox text on pages already classified `TOC`. Accept only if an Arabic sanity check passes (share of Arabic letters, no reversed-token patterns, dictionary hit rate on common words).
3. **`GeminiVisionTocSource`** — images of the contiguous run(s) of `TOC` pages (front or back, capped at `ktab.ocr.toc.max-pages`), using a revised `getTocPrompt()` with structured output:
   ```json
   { "entries": [
       { "title": "...", "divisionLabel": "الفصل", "ordinal": 3,
         "level": 1, "printedPageLabel": "٤٥", "sectionType": "CHAPTER" } ] }
   ```
   Multi-page TOCs go in one call so levels stay consistent.
4. **No TOC found** → Step 4 falls back to headings + running headers.

Raw result stored in `tbl_books.col_toc_raw`.

#### [NEW] `TocExtractionTasklet`
Runs the cascade, stores the raw TOC. No `JobExecutionContext` payload beyond the book id.

---

## Component 7 — Structure Resolution (Step 4, no LLM)

#### [NEW] `PageOffsetResolver`
1. Collect `(pageIndex, parsedPrintedNumber)` from `BODY` pages with numeric labels. `pageIndex` is the **book page index after spread splitting** — resolving against raw PDF pages would put every spread book off by a factor of ~2.
2. Compute `offset = pageIndex - printedNumber` per page.
3. Build **piecewise constant runs** with a sliding-window majority vote, so single OCR misreads don't break a run and plates/missing scans create a new run instead of a global error.
4. `Optional<Integer> toPageIndex(int printedNumber)` → uses the run covering that number; empty if uncovered.
5. Skipped entirely when `col_pagination_mode = NONE` (Component 2.6).

#### [NEW] `TocAligner`
For each TOC entry, in order:
1. Candidate page = `PageOffsetResolver.toPageIndex(printed)` (or the outline destination, mapped from PDF page to book page via `col_source_pdf_page`; for a split spread, the side is resolved by heading match).
2. Search detected headings (and running headers) on candidate ± `ktab.ocr.structure.search-window` (default 2) for a fuzzy match on normalized text (token-set similarity ≥ `ktab.ocr.structure.match-threshold`, default 0.8; title may be truncated in TOC or split across lines on the page).
3. Snap to the matched page and record `start_anchor`; confidence high. No match → keep candidate, confidence medium, `needs_review`.
4. No printed numbers (`PARTIAL` gaps or `NONE` mode), or TOC entries without page numbers → **monotonic sequence alignment** (DP) of TOC entries against the ordered list of detected headings across the book, with confidence capped per Component 2.6.
5. Enforce monotonic start pages; out-of-order results → `needs_review`.

#### [NEW] `HeadingsStructureBuilder` (fallback, no TOC)
Build sections from detected headings with `levelHint`, reinforced by running-header changes (a running header that changes is a strong chapter-boundary signal). Source `HEADINGS`, lower confidence.

#### [NEW] `SectionTreeBuilder`
- Build parent/child from `level`; compute `end_page` from the next sibling-or-ancestor start.
- Pages before the first body section → `FRONT_MATTER` section.
- Assign `page.section_id` = deepest section whose start ≤ page. Where a section starts mid-page, `start_anchor` lets consumers split the page's markdown at the heading.
- Book status: `RESOLVED` if all top-level sections have confidence ≥ threshold, else `NEEDS_REVIEW`.

#### [NEW] `StructureResolutionTasklet`
Idempotent: deletes non-`MANUAL` sections for the book and rebuilds. Sections with source `MANUAL` are kept and treated as ground truth.

---

## Component 8 — Stitch & Harmonize (Step 5, optional)

#### [NEW] `PageStitcher` (deterministic, always on)
For consecutive body pages where N `endsMidSentence` and N+1 `startsMidSentence`, record the join so exports concatenate without a paragraph break. Pure metadata; no text edits.

#### [NEW] `HarmonizationService` / `GeminiHarmonizationServiceImpl`
- Input: last paragraph of page N-1, full raw body of page N, first paragraph of page N+1 — **all read from the DB** — plus the page's section title (not the whole TOC).
- Text-only call; smaller/cheaper model allowed (`ktab.ocr.harmonize.model`).
- Allowed edits: remove stray artifacts (leftover page numbers, header fragments, broken line-wraps inside paragraphs, OCR noise characters). Forbidden: rewording, spelling/grammar correction, adding diacritics, reordering.
- **Guard:** compare normalized raw vs. clean (ArabicTextNormalizer, markup stripped). If similarity < `ktab.ocr.harmonize.min-similarity` (default 0.92) or length changes beyond ±8%, discard the output, keep `markdown_clean = null`, flag `HARMONIZE_REJECTED`.
- Partitioned by page ranges; parallel-safe because inputs come from persisted raw text.

---

## Component 9 — Quality Gate (Step 6)

Per book:
- % pages `DONE` / `FLAGGED` / `FAILED`, by flag.
- Section coverage: % body pages assigned to a non-front-matter section.
- Low-confidence section count.
- Arabic ratio per page (catches pages returned in the wrong script or transliterated).
- Image preparation: spreads split, rotations applied, `POOR` / `ILLEGIBLE_TEXT` / `BAD_SPLIT` / `UNSPLIT_SPREAD` counts, pagination mode.
- Rescan recommendation when the `POOR` ratio exceeds `ktab.ocr.quality.max-poor-ratio`.

Outcome written to `col_structure_status` and exposed via API. Nothing silently passes as "done" with unresolved flags.

---

## Component 10 — API & Job Wiring

#### [MODIFY] `OcrBatchConfig.java`
```java
@Bean
public Job ocrJob(Step decompositionStep, Step ocrStep, Step tocStep,
                  Step structureStep, Step harmonizeStep, Step qualityStep) {
    return new JobBuilder("ocrJob", jobRepository)
        .start(decompositionStep)
        .next(ocrStep)
        .next(tocStep)
        .next(structureStep)
        .next(harmonizeStep)   // no-op when disabled
        .next(qualityStep)
        .build();
}

@Bean
public Job restructureJob(Step tocStep, Step structureStep, Step qualityStep) { ... }
```

#### Endpoints
- `POST /api/ocr/books/{bookId}/start` — full pipeline (existing).
- `POST /api/ocr/books/{bookId}/restructure` — Steps 3–4–6 only, no OCR cost.
- `POST /api/ocr/books/{bookId}/harmonize` — Step 5–6 only.
- `POST /api/ocr/books/{bookId}/pages/retry-flagged` — re-OCR `FAILED`/`FLAGGED` pages only.
- `POST /api/ocr/books/{bookId}/pages/{pageNumber}/split` and `/rotate?degrees=` — manual image fixes; re-prepare that page, renumber if needed, re-OCR it.
- `PATCH /api/books/{bookId}` — set `readingDirection` before (re)decomposition.
- `GET  /api/books/{bookId}/structure` — section tree with page ranges, confidence, review flags.
- `PUT  /api/books/{bookId}/structure` — manual corrections (saved as `MANUAL`), then triggers page reassignment.

#### Properties
```properties
# Image preparation
ktab.ocr.image.dpi=300
ktab.ocr.image.retry-dpi=400
ktab.ocr.image.min-long-side-px=2000
ktab.ocr.image.max-long-side-px=3500
ktab.ocr.image.prefer-embedded=true
ktab.ocr.image.jpeg-quality=90
ktab.ocr.image.max-border-crop-ratio=0.15
ktab.ocr.spread.aspect-ratio-threshold=1.2
ktab.ocr.spread.gutter-band=0.40-0.60
ktab.ocr.orientation.precheck.enabled=true
ktab.ocr.orientation.thumbnail-long-side-px=768
ktab.ocr.quality.max-poor-ratio=0.10

# OCR
ktab.ocr.parallelism=8
ktab.ocr.rate-limit.requests-per-minute=60
ktab.ocr.prompt-version=v2
ktab.ocr.repetition.max-repeated-lines=5

ktab.ocr.toc.max-pages=20
ktab.ocr.structure.search-window=2
ktab.ocr.structure.match-threshold=0.80
ktab.ocr.structure.review-confidence=0.70
ktab.ocr.structure.no-pagination-confidence-cap=0.75
ktab.ocr.structure.pagination-printed-min-ratio=0.70
ktab.ocr.structure.pagination-none-max-ratio=0.20

ktab.ocr.harmonize.enabled=false
ktab.ocr.harmonize.model=
ktab.ocr.harmonize.min-similarity=0.92
```

---

## Summary Table

| # | Component | Files | Risk |
|---|---|---|---|
| 1 | Schema: sections table, page signals, image provenance, provenance | 1 migration, 2 entities | Medium (unique index on existing data; `page_number` semantics change) |
| 2 | Image preparation: render, border crop, spreads, orientation, quality, pagination mode | ~8 new, decomposition + reader modified | Medium–High (heuristics need tuning on real scans) |
| 3 | Enums: `PageKind`, `SectionType`, `StructureSource`, image enums | ~7 new | Very low |
| 4 | Arabic utilities: normalizer, label parser, classifier | 3 new | Low |
| 5 | Structured, stateless page OCR + failure detection + image retries | 3 modified | Medium (prompt/schema tuning) |
| 6 | TOC source cascade + tasklet | 5 new | Medium |
| 7 | Offset resolver, aligner, tree builder, tasklet | 5 new | Medium–High (core logic, well unit-testable) |
| 8 | Stitcher + guarded harmonization | 3 new | Low |
| 9 | Quality gate | 1 new | Low |
| 10 | Job wiring + endpoints | 2 modified, 1 new controller | Low |

---

## Delivery Phases

1. **Phase 1 — Image preparation + structured OCR.** Components 1, 2, 3, 5 (+ normalizer). Image preparation ships together with the new OCR step, because re-decomposing later changes page numbering. Immediate value: one correct image per book page, page kinds, clean body vs. footnotes, printed labels, image-quality and failure flags. Structure columns stay empty.
2. **Phase 2 — Structure.** Components 4, 6, 7, 9, pagination mode, `/restructure` and `GET /structure`. Runs over Phase 1 output without re-OCR.
3. **Phase 3 — Text quality.** Component 8.
4. **Phase 4 — Human in the loop.** `PUT /structure`, `retry-flagged`, manual split/rotate endpoints, review UI.

---

## Verification Plan

### Unit tests (no network)
- `ArabicTextNormalizerTest` — tashkeel, hamza forms, digit sets, leader dots.
- `PageLabelParserTest` — `45`, `٤٥`, `۴۵`, abjad `ج`, roman `xii`, garbage.
- `PageOffsetResolverTest` — constant offset; abjad front matter then numbered body; inserted plates (offset jump); single misread outlier; no labels.
- `TocAlignerTest` — exact match; heading one page off; truncated TOC title; TOC at end of book; no printed numbers (DP path); non-monotonic input.
- `SectionClassifierTest` — including `فهرس` ambiguity.
- `SectionTreeBuilderTest` — levels, end pages, front matter, `MANUAL` preservation.
- `RepetitionDetectorTest`, `HarmonizationGuardTest`.
- `PageRendererTest` — DPI clamp on tiny and oversized `MediaBox`; embedded-image extraction path.
- `BorderCropperTest` — black scanner bed; shadow on one edge; max crop ratio respected; page number near edge not cut.
- `SpreadDetectorTest` — true spread with centred gutter; off-centre gutter; landscape single page (table) not split; rotated portrait page not split; mixed single cover + inner spreads.
- `SpreadSplitterTest` — **RTL: right half gets the lower page number**; LTR reversed; split at gutter x, not midpoint.
- `PageRenumbererTest` — post-OCR split in the middle of a book shifts later pages transactionally.
- `OrientationHandlingTest` — 90/180/270 responses trigger exactly one rotate + retry; still unreadable → `FLAGGED`.
- `ImageQualityAnalyzerTest` — clean page vs. faded vs. bleed-through fixtures map to GOOD/FAIR/POOR bands.
- `PaginationModeDetectorTest` — thresholds for PRINTED / PARTIAL / NONE; confidence cap applied in NONE.

### Integration tests
- Fake Gemini client returning recorded fixtures (per page JSON), including `RECITATION` and `MAX_TOKENS` responses.
- Testcontainers PostgreSQL: run Flyway V5 on a copy of current schema; run full job on a 20-page fixture book; assert rerun is idempotent (no duplicate pages, identical sections).
- Scanned fixture PDF (~12 PDF pages) containing: single cover, 6 spreads, one page rotated 90°, one upside down, one faded page, one page with a library stamp and handwritten margin note. Assert 18 book pages in correct RTL order, rotations recorded, stamp/handwriting text absent from `bodyMarkdown`, faded page flagged.
- Memory test: decompose a 600+ page scanned PDF with a capped heap (e.g. `-Xmx512m`) without `OutOfMemoryError`.

```bash
./mvnw test -Dtest="ArabicTextNormalizerTest,PageLabelParserTest,PageOffsetResolverTest,TocAlignerTest,SectionClassifierTest,SectionTreeBuilderTest,RepetitionDetectorTest,HarmonizationGuardTest,PageRendererTest,BorderCropperTest,SpreadDetectorTest,SpreadSplitterTest,PageRenumbererTest,OrientationHandlingTest,ImageQualityAnalyzerTest,PaginationModeDetectorTest"
./mvnw verify -Pintegration
```

### Golden set (manual labels, reused for every prompt/model change)
Seven books covering:
1. Digital-born PDF with bookmarks.
2. Scanned, TOC at the front.
3. Scanned, TOC at the back (فهرس الموضوعات at end).
4. Scanned, no TOC.
5. Deep hierarchy (باب → فصل → مبحث → مطلب) with appendices and indices.
6. Older scan with two-page spreads, some rotated pages, dark borders and uneven quality (stamps, marginalia, faded pages).
7. Old printing or manuscript-style book with **no printed page numbers**.

Metrics tracked per run (with model + prompt version):
- Section start-page exact match (target ≥ 90%, ≥ 98% within ±1).
- Section type accuracy.
- Page kind accuracy.
- Character error rate on ~20 hand-transcribed sample pages.
- Harmonization rejection rate.
- Spread split accuracy (correct split + correct RTL order) and false-split rate on landscape single pages.
- Orientation correction accuracy.
- Share of illegible sentinels vs. manual assessment on POOR pages (the model should mark, not guess).
- Image tokens per page and CER at 200 / 300 / 400 DPI (used to set `ktab.ocr.image.dpi`).

### Manual checks
```sql
SELECT s.col_level, s.col_section_type, s.col_division_label, s.col_title,
       s.col_printed_start_label, s.col_start_page, s.col_end_page,
       s.col_source, s.col_confidence, s.col_needs_review
FROM tbl_book_sections s
WHERE s.col_book_id = :bookId
ORDER BY s.col_sort_order;

SELECT col_page_number, col_page_kind, col_printed_page_label,
       col_ocr_status, col_quality_flags
FROM tbl_book_pages
WHERE col_book_id = :bookId AND (col_ocr_status <> 'DONE' OR col_page_kind = 'UNKNOWN')
ORDER BY col_page_number;

-- Image preparation audit: spreads, rotations, poor pages
SELECT col_page_number, col_source_pdf_page, col_spread_side, col_rotation_degrees,
       col_render_dpi, col_image_quality, col_printed_page_label, col_quality_flags
FROM tbl_book_pages
WHERE col_book_id = :bookId
  AND (col_spread_side <> 'NONE' OR col_rotation_degrees <> 0 OR col_image_quality = 'POOR')
ORDER BY col_page_number;
```
