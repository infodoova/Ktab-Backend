-- ============================================================================
-- Flyway Migration V14: personalized storybook (docs/superpowers/plans/2026-09-24-storybook-02-domain-foundation.md)
-- All tables follow Ktab's tbl_/col_ naming and the BaseEntity audit block.
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_storybook_child_profiles (
    col_id               BIGSERIAL    PRIMARY KEY,
    col_owner_user_id    BIGINT       NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    col_name_ar          VARCHAR(40)  NOT NULL,
    col_gender           VARCHAR(10)  NOT NULL,
    col_age_band         VARCHAR(10)  NOT NULL,
    col_appearance       JSONB        NOT NULL,
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sb_child_owner ON tbl_storybook_child_profiles (col_owner_user_id);

CREATE TABLE IF NOT EXISTS tbl_storybooks (
    col_id                 BIGSERIAL     PRIMARY KEY,
    col_owner_user_id      BIGINT        NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    col_child_profile_id   BIGINT        NOT NULL REFERENCES tbl_storybook_child_profiles (col_id) ON DELETE CASCADE,
    col_blueprint_key      VARCHAR(80)   NOT NULL,
    col_blueprint_version  INTEGER       NOT NULL,
    col_inputs             JSONB         NOT NULL,  -- StoryInputs snapshot incl. the blueprint beats (decision D1)
    col_style              VARCHAR(40)   NOT NULL,
    col_language_variety   VARCHAR(20)   NOT NULL,
    col_tashkeel_level     VARCHAR(10)   NOT NULL,
    col_page_count         SMALLINT      NOT NULL CHECK (col_page_count IN (10, 12, 15)),
    col_status             VARCHAR(20)   NOT NULL,
    col_failed_from_status VARCHAR(20),
    col_failure_reason     TEXT,
    col_title_ar           VARCHAR(200),
    col_cover_scene_en     TEXT,
    col_dedication         VARCHAR(300),
    col_story_approved_at  TIMESTAMPTZ,
    col_look_approved_at   TIMESTAMPTZ,
    col_pdf_key            TEXT,
    col_look_regenerations INTEGER       NOT NULL DEFAULT 0,
    col_page_regenerations INTEGER       NOT NULL DEFAULT 0,
    col_total_cost_usd     NUMERIC(10,4) NOT NULL DEFAULT 0,
    col_created_by         BIGINT,
    col_last_modified_by   BIGINT,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sb_books_owner ON tbl_storybooks (col_owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sb_books_status ON tbl_storybooks (col_status);

CREATE TABLE IF NOT EXISTS tbl_storybook_characters (
    col_id                BIGSERIAL    PRIMARY KEY,
    col_storybook_id      BIGINT       NOT NULL REFERENCES tbl_storybooks (col_id) ON DELETE CASCADE,
    col_kind              VARCHAR(20)  NOT NULL,
    col_attributes        JSONB        NOT NULL,
    col_sheet_key         TEXT,
    col_sheet_version     INTEGER      NOT NULL DEFAULT 0,
    col_sheet_status      VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    col_photo_key         TEXT,          -- encrypted object in R2; NULL once purged
    col_photo_consent_at  TIMESTAMPTZ,
    col_photo_purged_at   TIMESTAMPTZ,
    col_created_by        BIGINT,
    col_last_modified_by  BIGINT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_character_kind UNIQUE (col_storybook_id, col_kind)
);

CREATE TABLE IF NOT EXISTS tbl_storybook_pages (
    col_id               BIGSERIAL    PRIMARY KEY,
    col_storybook_id     BIGINT       NOT NULL REFERENCES tbl_storybooks (col_id) ON DELETE CASCADE,
    col_page_index       SMALLINT     NOT NULL,   -- 0 = cover, 1..N = story pages
    col_kind             VARCHAR(10)  NOT NULL,
    col_text_ar          TEXT,                    -- NULL for the cover (title lives on the book)
    col_scene_en         TEXT         NOT NULL,
    col_characters       JSONB        NOT NULL DEFAULT '[]',
    col_text_zone        VARCHAR(10)  NOT NULL,
    col_critic_problems  JSONB,
    col_current_image_id BIGINT,
    col_generation       INTEGER      NOT NULL DEFAULT 0,
    -- first generation of the current round (first illustration, or a parent/admin regeneration);
    -- QA retries and the primary/fallback model choice count from here (sub-plan 05)
    col_round_start_generation INTEGER NOT NULL DEFAULT 1,
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_page_index UNIQUE (col_storybook_id, col_page_index)
);

CREATE TABLE IF NOT EXISTS tbl_storybook_page_images (
    col_id               BIGSERIAL     PRIMARY KEY,
    col_page_id          BIGINT        NOT NULL REFERENCES tbl_storybook_pages (col_id) ON DELETE CASCADE,
    col_generation       INTEGER       NOT NULL,
    col_image_key        TEXT          NOT NULL,
    col_model            VARCHAR(100)  NOT NULL,
    col_status           VARCHAR(20)   NOT NULL,
    col_qa_result        JSONB,
    col_cost_usd         NUMERIC(10,4) NOT NULL DEFAULT 0,
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_page_generation UNIQUE (col_page_id, col_generation)
);

ALTER TABLE tbl_storybook_pages
    ADD CONSTRAINT fk_sb_page_current_image
    FOREIGN KEY (col_current_image_id) REFERENCES tbl_storybook_page_images (col_id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS tbl_storybook_jobs (
    col_id               BIGSERIAL    PRIMARY KEY,
    col_storybook_id     BIGINT       NOT NULL REFERENCES tbl_storybooks (col_id) ON DELETE CASCADE,
    col_step             VARCHAR(30)  NOT NULL,
    col_page_index       SMALLINT     NOT NULL DEFAULT -1,  -- -1 for book-level steps
    col_generation       INTEGER      NOT NULL DEFAULT 0,
    col_idempotency_key  VARCHAR(120) NOT NULL,             -- book:step:page:generation
    col_status           VARCHAR(20)  NOT NULL,
    col_attempts         INTEGER      NOT NULL DEFAULT 0,
    col_next_run_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    col_locked_by        VARCHAR(100),
    col_locked_at        TIMESTAMPTZ,
    col_last_error       TEXT,
    col_finished_at      TIMESTAMPTZ,
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_job_idempotency UNIQUE (col_idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_sb_jobs_due ON tbl_storybook_jobs (col_next_run_at) WHERE col_status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_sb_jobs_book ON tbl_storybook_jobs (col_storybook_id);

-- The spec's generation_job "provider, model, cost" live here: one job can make several calls
-- (e.g. an image and its QA check). Kept when a book is deleted, for cost reporting.
CREATE TABLE IF NOT EXISTS tbl_storybook_ai_calls (
    col_id               BIGSERIAL     PRIMARY KEY,
    col_storybook_id     BIGINT        REFERENCES tbl_storybooks (col_id) ON DELETE SET NULL,
    col_job_id           BIGINT        REFERENCES tbl_storybook_jobs (col_id) ON DELETE SET NULL,
    col_purpose          VARCHAR(40)   NOT NULL,
    col_provider         VARCHAR(20)   NOT NULL,
    col_model            VARCHAR(100)  NOT NULL,
    col_input_tokens     BIGINT,
    col_output_tokens    BIGINT,
    col_images           INTEGER       NOT NULL DEFAULT 0,
    col_cost_usd         NUMERIC(10,6) NOT NULL,
    col_latency_ms       BIGINT        NOT NULL,
    col_success          BOOLEAN       NOT NULL,
    col_error            TEXT,
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sb_ai_calls_book ON tbl_storybook_ai_calls (col_storybook_id);
