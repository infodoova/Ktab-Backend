-- ============================================================================
-- Flyway Migration V3
-- Add Refresh Tokens entity for persistent authentication sessions
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_refresh_tokens (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_token               VARCHAR(500)    NOT NULL,
    col_user_id             BIGINT          NOT NULL,
    col_expires_at          TIMESTAMPTZ     NOT NULL,
    col_revoked             BOOLEAN         NOT NULL DEFAULT FALSE,
    col_replaced_by_token   VARCHAR(500),
    col_device_info         VARCHAR(255),

    -- Audit (embedded via BaseEntity)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,
    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_refresh_tokens_token UNIQUE (col_token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (col_user_id) REFERENCES tbl_users(col_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON tbl_refresh_tokens(col_token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON tbl_refresh_tokens(col_user_id);
