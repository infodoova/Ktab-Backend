-- ============================================================================
-- Flyway Migration V15: storybook credits (admin-granted until features.subscription ships)
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_storybook_credit_accounts (
    col_id               BIGSERIAL   PRIMARY KEY,
    col_user_id          BIGINT      NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    col_balance          INTEGER     NOT NULL DEFAULT 0 CHECK (col_balance >= 0),
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_credit_account_user UNIQUE (col_user_id)
);

CREATE TABLE IF NOT EXISTS tbl_storybook_credit_holds (
    col_id               BIGSERIAL   PRIMARY KEY,
    col_storybook_id     BIGINT      NOT NULL REFERENCES tbl_storybooks (col_id) ON DELETE CASCADE,
    col_user_id          BIGINT      NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    col_units            INTEGER     NOT NULL CHECK (col_units > 0),
    col_status           VARCHAR(20) NOT NULL,   -- HELD | COMMITTED | RELEASED
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_credit_hold_book UNIQUE (col_storybook_id)
);
