-- =============================================================================
-- Migration V18: Convert tbl_book_agent_records col_id from BIGSERIAL to UUID
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tbl_book_agent_records' AND column_name = 'col_id' AND data_type = 'bigint'
    ) THEN
        DROP INDEX IF EXISTS idx_book_agent_records_eviction_covering;
        ALTER TABLE tbl_book_agent_records ADD COLUMN col_uuid_id UUID DEFAULT gen_random_uuid() NOT NULL;
        ALTER TABLE tbl_book_agent_records DROP CONSTRAINT IF EXISTS tbl_book_agent_records_pkey;
        ALTER TABLE tbl_book_agent_records DROP COLUMN col_id;
        DROP SEQUENCE IF EXISTS tbl_book_agent_records_col_id_seq;
        ALTER TABLE tbl_book_agent_records RENAME COLUMN col_uuid_id TO col_id;
        ALTER TABLE tbl_book_agent_records ADD CONSTRAINT tbl_book_agent_records_pkey PRIMARY KEY (col_id);
        CREATE INDEX idx_book_agent_records_eviction_covering
            ON tbl_book_agent_records (col_book_id, col_count_used ASC, col_last_accessed_at ASC)
            INCLUDE (col_id);
    END IF;
END $$;

-- Ensure covering index exists
CREATE INDEX IF NOT EXISTS idx_book_agent_records_eviction_covering
    ON tbl_book_agent_records (col_book_id, col_count_used ASC, col_last_accessed_at ASC)
    INCLUDE (col_id);
