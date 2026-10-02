-- src/main/resources/db/migration/V23__book_trailer_cover_file.sql
-- ============================================================================
-- Flyway Migration V23: store uploaded cover file id for trailer sessions
-- ============================================================================

ALTER TABLE tbl_book_trailers
    ADD COLUMN IF NOT EXISTS col_cover_file_id VARCHAR(80);
