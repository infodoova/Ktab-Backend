-- ============================================================================
-- Flyway Migration V12: Studio ingestion + durable audiobook schema
-- See docs/ocr_engine_v3.md, Phase 3.
--
-- Three tables:
--   tbl_studio_projects / tbl_studio_chapters  -- ingestion state; purgeable scaffolding
--   tbl_book_audio_chapters                    -- durable, vendor-neutral audio (the product)
-- Plus four columns on tbl_book_sections / tbl_book_pages that turn projection into an
-- idempotent upsert instead of a rebuild (Studio's external ids stored on OUR rows).
-- ============================================================================

-- 1. Studio projects -----------------------------------------------------------
-- col_lifecycle is OURS and authoritative; Studio's own project "state" is never mirrored
-- or branched on here (it is opaque and can change under us).

CREATE TABLE IF NOT EXISTS tbl_studio_projects (
    col_id                   BIGSERIAL    PRIMARY KEY,
    col_book_id              BIGINT       NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_external_project_id  VARCHAR(64)  NOT NULL,
    col_lifecycle            VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
        -- PENDING|CREATED|SYNCED|CONVERTING|CONVERTED|ARCHIVED|FAILED|DELETED
    col_project_deleted_at   TIMESTAMPTZ,  -- NULL => possible orphan at ElevenLabs
    col_model_id             VARCHAR(100),
    col_title_voice_id       VARCHAR(64),
    col_paragraph_voice_id   VARCHAR(64),
    col_quality_preset       VARCHAR(30),
    col_pronunciation_dicts  JSONB,        -- [{id, versionId}] - reproducibility, not a catalogue
    col_raw_content_path     TEXT,         -- R2 key: full chapter JSON dump, for replay without re-calling the API
    col_last_synced_at       TIMESTAMPTZ,
    col_sync_error           TEXT,

    col_created_by           BIGINT,
    col_last_modified_by     BIGINT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                  INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT uq_studio_projects_external_id UNIQUE (col_external_project_id)
);

-- One live (non-terminal) project per book. BookContentPurger archives any live project
-- before a reroute/re-ingestion so this never blocks creating a fresh one.
CREATE UNIQUE INDEX IF NOT EXISTS uq_studio_projects_live_book
    ON tbl_studio_projects (col_book_id)
    WHERE col_lifecycle NOT IN ('DELETED', 'FAILED', 'ARCHIVED');

CREATE INDEX IF NOT EXISTS idx_studio_projects_book ON tbl_studio_projects (col_book_id);

-- 2. Studio chapters -------------------------------------------------------------
-- col_book_section_id is NOT NULL: a chapter row cannot exist without a known section.
-- This is "Ktab never infers correspondence" enforced by the schema, not by convention.

CREATE TABLE IF NOT EXISTS tbl_studio_chapters (
    col_id                     BIGSERIAL    PRIMARY KEY,
    col_project_id             BIGINT       NOT NULL REFERENCES tbl_studio_projects (col_id) ON DELETE CASCADE,
    col_external_chapter_id    VARCHAR(64)  NOT NULL,
    col_book_section_id        BIGINT       NOT NULL REFERENCES tbl_book_sections (col_id) ON DELETE CASCADE,
    col_origin                 VARCHAR(20)  NOT NULL,  -- STUDIO_PROJECTED | KTAB_PUSHED
    col_order_index            INT          NOT NULL,
    col_content_hash           CHAR(64),                -- sha256(canonicalText+structureFingerprint); makes polling cheap
    col_conversion_progress    NUMERIC(4,3) CHECK (col_conversion_progress BETWEEN 0 AND 1),
    col_last_conversion_error  TEXT,
    col_deleted_at             TIMESTAMPTZ,             -- tombstone; never hard-delete (audio may already be live)

    col_created_by             BIGINT,
    col_last_modified_by       BIGINT,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                    INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT uq_studio_chapters_external UNIQUE (col_project_id, col_external_chapter_id),
    -- Deferred: without this, Studio reordering two chapters aborts the sync transaction
    -- the moment the first UPDATE collides with the not-yet-updated second row.
    CONSTRAINT uq_studio_chapters_order UNIQUE (col_project_id, col_order_index)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS idx_studio_chapters_project ON tbl_studio_chapters (col_project_id);
CREATE INDEX IF NOT EXISTS idx_studio_chapters_section ON tbl_studio_chapters (col_book_section_id);

-- 3. Durable audio (the product) --------------------------------------------------
-- Vendor-neutral: no external_project_id/external_chapter_id here. After Studio project
-- cleanup, every row in tbl_studio_projects/tbl_studio_chapters for a book can be deleted
-- and every audiobook keeps working, because readers only ever query this table.

CREATE TABLE IF NOT EXISTS tbl_book_audio_chapters (
    col_id               BIGSERIAL    PRIMARY KEY,
    col_book_id          BIGINT       NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_book_section_id  BIGINT       NOT NULL REFERENCES tbl_book_sections (col_id) ON DELETE CASCADE,
    col_sort_order       INT          NOT NULL,
    col_audio_path       TEXT         NOT NULL,  -- R2: audio/{bookId}/chapters/ch-{sortOrder}.mp3
    col_timings_path     TEXT,                   -- R2: gzipped {pageId,charStart,charEnd,startMs,endMs}[]
    col_duration_ms      INT          NOT NULL,
    col_size_bytes       BIGINT       NOT NULL,
    col_sha256           CHAR(64)     NOT NULL,

    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT uq_book_audio_chapters_sort UNIQUE (col_book_id, col_sort_order)
);

CREATE INDEX IF NOT EXISTS idx_book_audio_chapters_book ON tbl_book_audio_chapters (col_book_id);

-- 4. Projection idempotency keys ----------------------------------------------------
-- Storing Studio's external ids on OUR rows turns projection into an upsert keyed on ids
-- Studio guarantees stable: re-sync a book a hundred times, identical col_id every time.

ALTER TABLE tbl_book_sections
    ADD COLUMN IF NOT EXISTS col_external_chapter_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_book_sections_external_chapter
    ON tbl_book_sections (col_book_id, col_external_chapter_id)
    WHERE col_external_chapter_id IS NOT NULL;

ALTER TABLE tbl_book_pages
    ADD COLUMN IF NOT EXISTS col_external_chapter_id  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS col_chapter_page_ordinal INT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_book_pages_external_chapter_ordinal
    ON tbl_book_pages (col_book_id, col_external_chapter_id, col_chapter_page_ordinal)
    WHERE col_external_chapter_id IS NOT NULL;
