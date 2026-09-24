-- ============================================================================
-- Flyway Migration V13: Align hash column types to VARCHAR(64)
-- Avoids bpchar vs varchar type mismatch during Hibernate schema validation.
-- ============================================================================

ALTER TABLE tbl_book_audio_chapters
    ALTER COLUMN col_sha256 TYPE VARCHAR(64);

ALTER TABLE tbl_studio_chapters
    ALTER COLUMN col_content_hash TYPE VARCHAR(64);
