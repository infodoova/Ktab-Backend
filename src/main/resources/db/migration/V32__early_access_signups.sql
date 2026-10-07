-- ============================================================================
-- Flyway Migration V32: early-access signups
--
-- People who ask for early access before (or without) having an account. Deliberately separate from tbl_users and the
-- authentication tables: it holds contact details and plan interest only, no password and no role, and nothing here
-- can be used to log in.
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_early_access_signups (
    col_id                BIGSERIAL     PRIMARY KEY,
    col_email             VARCHAR(255)  NOT NULL,
    col_full_name         VARCHAR(200)  NOT NULL,
    col_phone_number      VARCHAR(32),
    col_gender            VARCHAR(10),
    -- true once early access has been granted to this person
    col_early_access      BOOLEAN       NOT NULL DEFAULT FALSE,
    -- the plan this person asked for or was given, as a plan key; free text until the plan catalog exists
    col_plan              VARCHAR(50),
    col_created_by        BIGINT,
    col_last_modified_by  BIGINT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT ck_early_access_gender CHECK (col_gender IS NULL OR col_gender IN ('MALE', 'FEMALE'))
);

-- One signup per address, whatever the capitalization.
CREATE UNIQUE INDEX IF NOT EXISTS uq_early_access_email ON tbl_early_access_signups (lower(col_email));
CREATE INDEX IF NOT EXISTS idx_early_access_granted ON tbl_early_access_signups (col_early_access) WHERE col_early_access;
