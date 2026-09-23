-- ============================================================================
-- Flyway Migration V9: PDF classification for dual-pipeline ingestion
-- See docs/ocr_engine_v3.md, Phase 0.
--
-- Classification runs once per book before any rendering/OCR happens, and
-- decides which independent pipeline (studioIngestionJob vs ocrJob) owns
-- text and structure extraction for that book.
-- ============================================================================

ALTER TABLE tbl_books
    ADD COLUMN IF NOT EXISTS col_pdf_type            VARCHAR(20),   -- PdfType enum; null until classified
    ADD COLUMN IF NOT EXISTS col_ingestion_route      VARCHAR(20),   -- IngestionRoute enum; admin-overridable
    ADD COLUMN IF NOT EXISTS col_pdf_classification    JSONB,        -- per-page evidence, sampling info
    ADD COLUMN IF NOT EXISTS col_classifier_version    VARCHAR(10);  -- e.g. 'v1'; allows re-classifying a corpus after tuning

CREATE INDEX IF NOT EXISTS idx_books_pdf_type ON tbl_books (col_pdf_type);
CREATE INDEX IF NOT EXISTS idx_books_ingestion_route ON tbl_books (col_ingestion_route);
