# Arabic Book Extraction Service — Spec (product owner, 2026-10-01)

Source of truth for `docs/superpowers/plans/2026-10-01-native-extraction-and-tts.md`.
Context: ElevenLabs has not granted Studio API access, so digital-PDF ingestion and audiobooks need a
Ktab-owned path. A single flag switches between Studio (when access arrives) and the Ktab path.

## Goal

A Spring Boot Arabic Book Extraction Service that accepts a digital Arabic PDF and returns structured book data:
metadata; page-by-page text; table of contents; chapters; sections; subsections; clean extracted Arabic text;
structure detection source and confidence. This extraction phase itself does not include TTS, OCR or ElevenLabs.

## Requirements (condensed from the owner's spec; numbering kept)

1. **Ingestion:** `POST /api/books/extract`, multipart PDF. Validate: not empty, is a PDF, size within limit,
   not corrupted, not encrypted/password-protected, contains extractable text.
2. **Metadata:** title, author, subject, keywords, language, pageCount. Never assume it is present or accurate.
3. **Page by page:** extract each page independently (`pdfPage`, `rawText`, `cleanedText`), keeping the physical
   1-based PDF page number. Preserve all Arabic letters (أ إ آ ة ى ؤ ئ) and tashkeel; do not aggressively normalize.
4. **Cleaning:** remove or mark repeated headers/footers, repeated book title/author lines, standalone page
   numbers, excess empty lines, duplicate whitespace, obvious extraction artifacts. Keep `rawText` and
   `cleanedText`; never overwrite the raw text.
5. **Structure priority:** 1) embedded PDF outline, 2) printed Arabic TOC, 3) heading detection.
6. **Embedded outline:** title, level, destination page (PDFBox `PDDocumentOutline`). Preferred source.
7. **Printed TOC detection:** search roughly the first 30–40 pages for الفهرس / فهرس المحتويات / المحتويات /
   المحتويات العامة.
8. **Page numbers:** Western (123), Arabic-Indic (١٢٣), Persian (۱۲۳); normalize digits only for parsing.
9. **Parse TOC entries:** `title ........ ١٢` → title + printed page.
10. **Printed → PDF page:** never assume they are equal; find the heading near the expected place, compute the
    offset, validate titles near the computed destination.
11. **Heading fallback:** المقدمة, مقدمة المؤلف, تمهيد, الفصل …, الباب …, القسم …, الجزء …, المبحث …, الخاتمة, خاتمة.
    Regex plus signals: short isolated line, near the top of the page, surrounded by empty lines, larger font when
    available, repeated pattern through the book, followed by substantial content.
12. **One model:** `TocEntry(title, level, startPage, type)`, types INTRODUCTION, CHAPTER, SECTION, SUBSECTION,
    CONCLUSION, OTHER.
13. **Hierarchy preserved** (الفصل → المبحث → المطلب), not flattened.
14. **Chapter ranges:** each entry ends one page before the next entry starts; the last ends at the last page.
15. **Chapter model:** index, title, startPage, endPage, text, pages, sections.
16. **Keep page relationships:** Book → Chapter → Section → Page → Text.
17. **Detection source + confidence:** EMBEDDED_OUTLINE (high), PRINTED_TOC (high/medium), HEADING_DETECTION
    (medium), NONE.
18. **API result:** metadata, structureDetection {source, confidence}, toc, chapters (+ warnings).

Phase 5 quality checks return **warnings** (missing chapters, duplicate headings, invalid ranges, TOC pages outside
the document, empty chapter text, unexpectedly short chapters, large gaps, low Arabic character ratio, extraction
artifacts), e.g. `{"code":"TOC_PAGE_MISMATCH","message":"…"}`, instead of silently accepting suspicious structure.

## Version 1 scope

In: digital PDFs with selectable Arabic text, normal single-column books, embedded bookmarks, printed Arabic TOCs,
common chapter structures, Arabic/Persian/Western page numbers.
Out: OCR, scanned books, magazines, complex multi-column layouts, AI-based document understanding.

## Switch requirement (owner, 2026-10-01)

"Make a flag to use the Studio API: if true use it; if false use my own extraction service and my own TTS."
