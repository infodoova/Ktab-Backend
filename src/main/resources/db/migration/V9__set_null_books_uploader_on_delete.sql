-- ============================================================================
-- Flyway Migration V9
-- Update fk_books_uploader constraint with ON DELETE SET NULL
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_books_uploader'
    ) THEN
        ALTER TABLE tbl_books DROP CONSTRAINT fk_books_uploader;
    END IF;

    ALTER TABLE tbl_books
        ADD CONSTRAINT fk_books_uploader
        FOREIGN KEY (col_uploader_id)
        REFERENCES tbl_users(col_id)
        ON DELETE SET NULL;
END $$;
