-- Legacy answers intentionally remain unversioned and refresh on next request.
ALTER TABLE tbl_book_agent_records
    ADD COLUMN col_cache_revision VARCHAR(64),
    ADD COLUMN col_answer_generated_at TIMESTAMP WITH TIME ZONE;
