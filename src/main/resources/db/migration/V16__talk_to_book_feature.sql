-- ============================================================================
-- Flyway Migration V16: Talk to Book Feature & Semantic Cache Records
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_book_agent_records (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_book_id             BIGINT          NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_question            VARCHAR(500)    NOT NULL,
    col_question_hash       VARCHAR(64)     NOT NULL,
    col_question_embedding  JSONB,
    col_answer              TEXT            NOT NULL,
    col_cited_pages         JSONB,
    col_count_used          INTEGER         NOT NULL DEFAULT 1 CHECK (col_count_used >= 1),
    col_is_web_augmented    BOOLEAN         NOT NULL DEFAULT FALSE,
    col_last_accessed_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- BaseEntity Audit fields
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 INTEGER         NOT NULL DEFAULT 0
);

-- Index for instant exact-match cache lookups (O(1))
CREATE INDEX IF NOT EXISTS idx_book_agent_records_book_hash
    ON tbl_book_agent_records (col_book_id, col_question_hash);

-- Index for LFU / LRU eviction queries (ordered by usage count, then oldest access)
CREATE INDEX IF NOT EXISTS idx_book_agent_records_eviction
    ON tbl_book_agent_records (col_book_id, col_count_used ASC, col_last_accessed_at ASC);

-- Index for book association lookups and cascade counts
CREATE INDEX IF NOT EXISTS idx_book_agent_records_book_id
    ON tbl_book_agent_records (col_book_id);

-- Optional Full-Text search index on book pages for hybrid keyword search
CREATE INDEX IF NOT EXISTS idx_book_pages_markdown_clean_fts
    ON tbl_book_pages USING gin (to_tsvector('simple', coalesce(col_markdown_clean, col_markdown_content, '')));
