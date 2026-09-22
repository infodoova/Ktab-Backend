-- ============================================================================
-- Flyway Migration V6: OCR Engine v2 Structure-Aware Book Processor
-- Preserves existing tables, columns, and data completely.
-- ============================================================================

-- 1. Sections: the book's structure tree
CREATE TABLE IF NOT EXISTS tbl_book_sections (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_book_id             BIGINT          NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_parent_id           BIGINT          REFERENCES tbl_book_sections (col_id) ON DELETE CASCADE,
    col_section_type        VARCHAR(30)     NOT NULL,          -- SectionType enum
    col_level               SMALLINT        NOT NULL,          -- 0 = top level
    col_sort_order          INTEGER         NOT NULL,
    col_division_label      VARCHAR(50),                       -- e.g. 'الباب', 'الفصل', 'المبحث'
    col_ordinal             INTEGER,                           -- parsed from 'الفصل الثالث' -> 3, if any
    col_title               VARCHAR(1000)   NOT NULL,
    col_title_normalized    VARCHAR(1000)   NOT NULL,          -- for fuzzy matching
    col_printed_start_label VARCHAR(20),                       -- as printed in TOC: '45', '٤٥', 'ج'
    col_start_page          INTEGER,                           -- book page index (col_page_number)
    col_end_page            INTEGER,
    col_start_anchor        VARCHAR(1000),                     -- heading text as it appears in page markdown
    col_source              VARCHAR(20)     NOT NULL,          -- PDF_OUTLINE | TEXT_LAYER | TOC_VISION | HEADINGS | MANUAL
    col_confidence          NUMERIC(3,2)    NOT NULL DEFAULT 0,
    col_needs_review        BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Audit & Optimistic Locking (BaseEntity conformance)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 INTEGER         NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_book_sections_book ON tbl_book_sections (col_book_id, col_sort_order);
CREATE INDEX IF NOT EXISTS idx_book_sections_parent ON tbl_book_sections (col_parent_id);

-- 2. Pages: image provenance + per-page structure signals + clean text
-- Allow col_markdown_content to be NULL temporarily when decomposition manifests pending pages
ALTER TABLE tbl_book_pages ALTER COLUMN col_markdown_content DROP NOT NULL;

ALTER TABLE tbl_book_pages
    ADD COLUMN IF NOT EXISTS col_source_pdf_page      INTEGER,
    ADD COLUMN IF NOT EXISTS col_spread_side          VARCHAR(10) NOT NULL DEFAULT 'NONE', -- NONE|RIGHT|LEFT
    ADD COLUMN IF NOT EXISTS col_rotation_degrees     SMALLINT    NOT NULL DEFAULT 0,      -- 0|90|180|270
    ADD COLUMN IF NOT EXISTS col_render_dpi           SMALLINT,
    ADD COLUMN IF NOT EXISTS col_image_width          INTEGER,
    ADD COLUMN IF NOT EXISTS col_image_height         INTEGER,
    ADD COLUMN IF NOT EXISTS col_image_quality        VARCHAR(10),                         -- GOOD|FAIR|POOR
    ADD COLUMN IF NOT EXISTS col_image_metrics        JSONB,
    ADD COLUMN IF NOT EXISTS col_page_kind            VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS col_printed_page_label   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS col_running_header       VARCHAR(500),
    ADD COLUMN IF NOT EXISTS col_headings             JSONB,
    ADD COLUMN IF NOT EXISTS col_footnotes_markdown   TEXT,
    ADD COLUMN IF NOT EXISTS col_starts_mid_sentence  BOOLEAN,
    ADD COLUMN IF NOT EXISTS col_ends_mid_sentence    BOOLEAN,
    ADD COLUMN IF NOT EXISTS col_markdown_clean       TEXT,
    ADD COLUMN IF NOT EXISTS col_section_id           BIGINT REFERENCES tbl_book_sections (col_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS col_quality_flags        JSONB,
    ADD COLUMN IF NOT EXISTS col_ocr_model            VARCHAR(100),
    ADD COLUMN IF NOT EXISTS col_prompt_version       VARCHAR(20);

-- Safe backfill for existing rows: preserves 1:1 mapping with source PDF page
UPDATE tbl_book_pages
SET col_source_pdf_page = col_page_number
WHERE col_source_pdf_page IS NULL;

CREATE INDEX IF NOT EXISTS idx_book_pages_source_pdf ON tbl_book_pages (col_book_id, col_source_pdf_page, col_spread_side);
CREATE INDEX IF NOT EXISTS idx_book_pages_section ON tbl_book_pages (col_book_id, col_section_id);

-- 3. Book-level structure + scan status
ALTER TABLE tbl_books
    ADD COLUMN IF NOT EXISTS col_reading_direction    VARCHAR(3)  NOT NULL DEFAULT 'RTL', -- RTL|LTR
    ADD COLUMN IF NOT EXISTS col_pagination_mode      VARCHAR(10),                         -- PRINTED|PARTIAL|NONE
    ADD COLUMN IF NOT EXISTS col_structure_status     VARCHAR(20) DEFAULT 'NONE',          -- NONE|RESOLVED|NEEDS_REVIEW|MANUAL
    ADD COLUMN IF NOT EXISTS col_structure_source     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS col_toc_raw              JSONB;
