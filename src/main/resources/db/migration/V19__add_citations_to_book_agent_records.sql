-- =============================================================================
-- Migration V19: Add col_citations to tbl_book_agent_records for verbatim snippets
-- =============================================================================

ALTER TABLE tbl_book_agent_records
    ADD COLUMN IF NOT EXISTS col_citations JSONB;
