-- ============================================================================
-- Flyway Migration V22: track every Higgsfield generate call, not just jobs created
-- ============================================================================
-- V21 already shipped col_higgsfield_generations (jobs Higgsfield actually created) and is applied on
-- environments that ran it, so it is never edited after the fact. This adds a second counter: every
-- generate_video call, including ones rejected by a concurrency limit or answered with a preset
-- suggestion instead of a job. See SessionEvents#interpret, which now tells the two apart.

ALTER TABLE tbl_book_trailers
    ADD COLUMN IF NOT EXISTS col_higgsfield_calls INTEGER NOT NULL DEFAULT 0;
