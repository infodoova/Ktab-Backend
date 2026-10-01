# Switching between Studio and Ktab's own extraction + TTS

Two flags decide the pipeline. Both default to **false**.

| Flag | `false` (default) | `true` |
|---|---|---|
| `KTAB_STUDIO_ENABLED` | Digital PDFs → `nativeIngestionJob`; every audiobook → `nativeAudiobookJob` | Digital PDFs → `studioIngestionJob`; audiobooks → `studioAudiobookJob` (as before this change) |
| `KTAB_OCR_ENABLED` | No OCR at all: scanned, hybrid, mixed and unknown PDFs also go to `nativeIngestionJob`; every OCR launch point answers 409 | OCR routing as before this change |

The flags are read **when a job is launched**. A running job always finishes on the pipeline it started on.

## What the Ktab path does

**Extraction** (`features/extraction`, `POST /api/books/extract` and `nativeIngestionJob`):
validates the PDF (not empty, is a PDF, size, not corrupt, not encrypted, has a text layer), reads metadata, extracts every page
with its physical page number, removes repeated headers/footers and standalone page numbers (raw text is never overwritten),
then finds the structure: embedded outline → printed Arabic فهرس (with printed→PDF page offset) → heading detection.
The result is a tree (chapters → sections), chapter page ranges, the detection source and confidence, and warnings.
The job writes the same tables Studio and OCR write (`tbl_book_pages`, `tbl_book_sections`, `tbl_books`).

**Audiobook** (`features/nativetts`, `nativeAudiobookJob`): one audio chapter per top-level section. Text is cut into
chunks (paragraph → sentence → whitespace, never inside a word), each chunk goes to ElevenLabs' standard
`/v1/text-to-speech/{voice}/with-timestamps`, FFmpeg joins the chunks, and the per-character timings are shifted by the *measured* length
of the chunks before them. It writes exactly what Studio writes:
`audio/{bookId}/chapters/ch-NNNN.mp3`, `audio/{bookId}/timings/ch-NNNN.json.gz` (columnar, gzipped) and `tbl_book_audio_chapters` rows,
then sets `hasAudio`. A finished chapter is committed on its own, so a re-run skips it.

The audiobook endpoint is unchanged: `POST /api/studio/books/{id}/audiobook`, `GET /api/studio/books/{id}/status` (responses now include `pipeline`).

## Settings

| Variable | Meaning |
|---|---|
| `KTAB_STUDIO_ENABLED`, `KTAB_OCR_ENABLED` | the switches above |
| `KTAB_NATIVE_TTS_VOICE_ID` | **Required before the first audiobook.** No default: the job refuses to start while blank |
| `KTAB_NATIVE_TTS_MODEL_ID` | default `eleven_multilingual_v2`. `eleven_v3` also returns timings but rejects neighbouring-text context and requests over 5,000 characters; the code adapts automatically (no context, 4,500-char chunks) |
| `KTAB_NATIVE_TTS_MAX_CHARS` | characters per TTS request, default 2500 |
| `KTAB_NATIVE_TTS_MAX_CHARS_PER_BOOK` | the job fails before spending anything above this, default 1,500,000 |
| `KTAB_EXTRACTION_MAX_UPLOAD_BYTES` | largest PDF the native job accepts, default 200 MB |

Spring's global upload limit (`spring.servlet.multipart.max-file-size=50MB`) was left unchanged, so `POST /api/books/extract` accepts up to 50 MB.

## When Studio access arrives

1. Set `KTAB_STUDIO_ENABLED=true` and restart. New digital books and new audiobook requests use Studio.
2. Books already ingested natively keep their pages and audio. Re-ingesting one (the existing re-ingest admin endpoint) runs it through Studio.
3. Rollback: set `KTAB_STUDIO_ENABLED=false`.

Turning OCR back on later: `KTAB_OCR_ENABLED=true`. Scanned books route to `ocrJob` again, exactly as before.

## Known V1 limits

- Fully scanned PDFs are rejected ("This PDF has no text layer; scanned books are not supported while OCR is off"). Scanned pages inside a
  mostly-digital book are kept as empty `IMAGE_ONLY` pages and listed in an `IMAGE_ONLY_PAGES` warning (an error above 20% of the book).
- A chapter that starts in the middle of a page: the whole page goes to the later chapter (the audiobook reads whole pages).
- Heading detection ignores PDF font sizes. Multi-column layouts and magazines are out of scope.
- A book with no detectable structure is saved as one whole-book section marked `needsReview`, and the book's structure status is `NEEDS_REVIEW`.
- Not yet checked with a real published Arabic book: the order PDFBox returns TOC lines for real printed فهرس pages (the parser accepts
  the page number at either end of the line, which is what Chromium-made PDFs produce). Run one bookmarked book and one printed-فهرس book
  through `POST /api/books/extract?includePages=true` and compare the chapters with the real table of contents before relying on it.

## Tests that need a live service (skipped by default)

- `ExtractionFixtureGenerator` (`GENERATE_EXTRACTION_FIXTURES=true`): regenerates the committed test PDFs with Chromium.
- `NativeTtsLiveTest` (`NATIVE_TTS_LIVE=true NATIVE_TTS_LIVE_VOICE=<voice> ELEVENLABS_API_KEY=...`): a real ElevenLabs call (a few hundred to ~1,400 characters of credit).
