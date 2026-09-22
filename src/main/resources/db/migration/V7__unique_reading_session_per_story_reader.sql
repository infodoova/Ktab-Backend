-- Enforce one reading session per story and reader.
-- Existing duplicate sessions are collapsed by keeping the newest session.

DO $$
DECLARE
    duplicate RECORD;
BEGIN
    FOR duplicate IN
        SELECT col_story_id, col_reader_id, MAX(col_id) AS keep_session_id
        FROM tbl_reading_sessions
        GROUP BY col_story_id, col_reader_id
        HAVING COUNT(*) > 1
    LOOP
        -- Turns belong to a session and cannot be reassigned safely when their
        -- turn indexes collide, so retain the newest session's complete state.
        DELETE FROM tbl_turns
        WHERE col_session_id IN (
            SELECT col_id
            FROM tbl_reading_sessions
            WHERE col_story_id = duplicate.col_story_id
              AND col_reader_id = duplicate.col_reader_id
              AND col_id <> duplicate.keep_session_id
        );

        DELETE FROM tbl_reading_sessions
        WHERE col_story_id = duplicate.col_story_id
          AND col_reader_id = duplicate.col_reader_id
          AND col_id <> duplicate.keep_session_id;
    END LOOP;
END $$;

ALTER TABLE tbl_reading_sessions
    ADD CONSTRAINT uq_reading_sessions_story_reader
    UNIQUE (col_story_id, col_reader_id);
