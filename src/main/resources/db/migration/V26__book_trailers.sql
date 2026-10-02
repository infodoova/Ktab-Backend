-- src/main/resources/db/migration/V21__book_trailers.sql
-- ============================================================================
-- Flyway Migration V21: book trailers produced by the Claude Managed Agents trailer agent
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_book_trailers (
    col_id                    BIGSERIAL    PRIMARY KEY,
    col_book_id               BIGINT       NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_requested_by          BIGINT       REFERENCES tbl_users (col_id) ON DELETE SET NULL,
    col_status                VARCHAR(20)  NOT NULL,
    col_session_id            VARCHAR(80),
    col_book_file_id          VARCHAR(80),
    col_agent_version         INTEGER,
    col_outcome_result        VARCHAR(40),
    col_outcome_explanation   TEXT,
    col_higgsfield_generations INTEGER     NOT NULL DEFAULT 0,
    col_video_key             VARCHAR(300),
    col_clean_video_key       VARCHAR(300),
    col_captions_key          VARCHAR(300),
    col_qc_report             TEXT,
    col_error                 TEXT,
    col_next_check_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    col_started_at            TIMESTAMPTZ,
    col_finished_at           TIMESTAMPTZ,
    col_created_by            BIGINT,
    col_last_modified_by      BIGINT,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                   INTEGER      NOT NULL DEFAULT 0
);

-- One active trailer per book (race-safe across double clicks and instances). Must match TrailerStatus.ACTIVE.
CREATE UNIQUE INDEX IF NOT EXISTS uq_book_trailer_active ON tbl_book_trailers (col_book_id)
    WHERE col_status IN ('QUEUED', 'RUNNING', 'HARVESTING');
CREATE UNIQUE INDEX IF NOT EXISTS uq_book_trailer_session ON tbl_book_trailers (col_session_id)
    WHERE col_session_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_book_trailer_due ON tbl_book_trailers (col_status, col_next_check_at);
CREATE INDEX IF NOT EXISTS idx_book_trailer_book ON tbl_book_trailers (col_book_id, created_at);

-- Short-lived PKCE state for the admin "Connect Higgsfield" flow (D8).
CREATE TABLE IF NOT EXISTS tbl_trailer_oauth_states (
    col_id                BIGSERIAL    PRIMARY KEY,
    col_state             VARCHAR(100) NOT NULL,
    col_code_verifier     VARCHAR(200) NOT NULL,
    col_client_id         VARCHAR(200) NOT NULL,
    col_redirect_uri      VARCHAR(500) NOT NULL,
    col_admin_user_id     BIGINT       NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    col_used              BOOLEAN      NOT NULL DEFAULT FALSE,
    col_created_by        BIGINT,
    col_last_modified_by  BIGINT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_trailer_oauth_state UNIQUE (col_state)
);
