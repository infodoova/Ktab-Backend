-- ============================================================================
-- Flyway Migration V17: Talk to Book Constraints and Covering Indexes
-- ============================================================================

-- 1. Remove old non-unique index on (book_id, question_hash)
DROP INDEX IF EXISTS idx_book_agent_records_book_hash;

-- 2. Add DB-level uniqueness constraint preventing race conditions on concurrent identical questions
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_book_agent_records_book_hash'
    ) THEN
        ALTER TABLE tbl_book_agent_records
            ADD CONSTRAINT uq_book_agent_records_book_hash UNIQUE (col_book_id, col_question_hash);
    END IF;
END $$;

-- 3. Replace eviction index with covering index using INCLUDE (col_id) for pure Index-Only Scans
DROP INDEX IF EXISTS idx_book_agent_records_eviction;

CREATE INDEX IF NOT EXISTS idx_book_agent_records_eviction_covering
    ON tbl_book_agent_records (col_book_id, col_count_used ASC, col_last_accessed_at ASC)
    INCLUDE (col_id);

-- 4. Add data integrity check constraints
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_book_agent_records_question_min_length'
    ) THEN
        ALTER TABLE tbl_book_agent_records
            ADD CONSTRAINT chk_book_agent_records_question_min_length CHECK (length(trim(col_question)) >= 3);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_book_agent_records_hash_length'
    ) THEN
        ALTER TABLE tbl_book_agent_records
            ADD CONSTRAINT chk_book_agent_records_hash_length CHECK (length(col_question_hash) = 64);
    END IF;
END $$;
