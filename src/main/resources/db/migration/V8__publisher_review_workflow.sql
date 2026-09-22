-- ============================================================================
-- Flyway Migration V8
-- Add PUBLISHER editorial review workflow: DRAFT -> UNDER_REVIEW -> PUBLISHED
-- ============================================================================

-- 1. Add review-workflow columns to tbl_books
ALTER TABLE tbl_books
    ADD COLUMN IF NOT EXISTS col_submitted_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS col_reviewed_by    BIGINT,
    ADD COLUMN IF NOT EXISTS col_reviewed_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS col_review_note    TEXT;

-- 2. FK: reviewed_by -> tbl_users
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_books_reviewed_by'
    ) THEN
        ALTER TABLE tbl_books
            ADD CONSTRAINT fk_books_reviewed_by
            FOREIGN KEY (col_reviewed_by)
            REFERENCES tbl_users(col_id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- 3. Index to back the publisher review queue (status + submission order)
CREATE INDEX IF NOT EXISTS idx_books_review_queue ON tbl_books(col_status, col_submitted_at);
