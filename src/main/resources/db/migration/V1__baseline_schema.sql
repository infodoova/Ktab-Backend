-- ============================================================================
-- Flyway Baseline Migration V1
-- Ktab-Backend: Full schema derived from JPA entities
-- ============================================================================
-- This migration creates the complete database schema for the Ktab platform.
-- It uses "CREATE TABLE IF NOT EXISTS" so it is safe to run against an
-- existing database that was previously managed by Hibernate ddl-auto=update.
-- ============================================================================

-- ============================================================================
-- 1. USERS & AUTH
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_users (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_email               VARCHAR(255)    NOT NULL,
    col_first_name          VARCHAR(255)    NOT NULL,
    col_middle_name         VARCHAR(255),
    col_last_name           VARCHAR(255)    NOT NULL,
    col_password_digest     VARCHAR(100)    NOT NULL,
    col_role                VARCHAR(255),
    col_is_active           VARCHAR(255),

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_users_email UNIQUE (col_email)
);

CREATE TABLE IF NOT EXISTS tbl_user_codes (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_code                VARCHAR(255)    NOT NULL,
    col_code_type           VARCHAR(255)    NOT NULL,
    col_expires_at          TIMESTAMPTZ     NOT NULL,
    col_is_used             BOOLEAN         DEFAULT FALSE,
    col_user_id             BIGINT          NOT NULL,

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT fk_user_codes_user FOREIGN KEY (col_user_id) REFERENCES tbl_users(col_id)
);

CREATE TABLE IF NOT EXISTS tbl_user_settings (
    col_id                      BIGSERIAL       PRIMARY KEY,
    col_user_id                 BIGINT          NOT NULL,
    col_language                VARCHAR(5)      NOT NULL DEFAULT 'en',
    col_timezone                VARCHAR(50)     NOT NULL DEFAULT 'UTC',
    col_notifications_email     BOOLEAN         NOT NULL DEFAULT TRUE,
    col_notifications_in_app    BOOLEAN         NOT NULL DEFAULT TRUE,
    col_privacy_profile_public  BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Audit (embedded)
    col_created_by              BIGINT,
    col_last_modified_by        BIGINT,
    created_at                  TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ,

    version                     INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_user_settings_user_id UNIQUE (col_user_id),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (col_user_id) REFERENCES tbl_users(col_id)
);

-- Self-referencing FKs for audit columns on tbl_users
ALTER TABLE tbl_users
    ADD CONSTRAINT fk_users_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id);

ALTER TABLE tbl_users
    ADD CONSTRAINT fk_users_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id);

-- ============================================================================
-- 2. CONFIGURATION (Genres)
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_main_genres (
    col_id                  BIGSERIAL       PRIMARY KEY,
    name_en                 VARCHAR(255),
    name_ar                 VARCHAR(255),
    description             VARCHAR(255),
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_main_genre_name_ar UNIQUE (name_ar),
    CONSTRAINT uq_main_genre_name_en UNIQUE (name_en),

    CONSTRAINT fk_main_genres_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_main_genres_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

CREATE TABLE IF NOT EXISTS tbl_sub_genres (
    col_id                  BIGSERIAL       PRIMARY KEY,
    name_en                 VARCHAR(255),
    name_ar                 VARCHAR(255),
    description             VARCHAR(255),
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    main_genre_id           BIGINT          NOT NULL,

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_sub_genre_name_ar_per_main UNIQUE (name_ar, main_genre_id),
    CONSTRAINT uq_sub_genre_name_en_per_main UNIQUE (name_en, main_genre_id),
    CONSTRAINT fk_sub_genres_main_genre FOREIGN KEY (main_genre_id) REFERENCES tbl_main_genres(col_id),

    CONSTRAINT fk_sub_genres_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_sub_genres_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

-- ============================================================================
-- 3. BOOKS
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_books (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_author_id           BIGINT          NOT NULL,
    col_title               VARCHAR(255)    NOT NULL,
    col_description         TEXT,
    col_language            VARCHAR(255),
    col_age_range_min       INTEGER,
    col_age_range_max       INTEGER,
    col_page_count          INTEGER,
    col_has_audio           BOOLEAN         DEFAULT FALSE,
    col_average_rating      NUMERIC(3, 2)   DEFAULT 0.00,
    col_total_reviews       INTEGER         DEFAULT 0,
    col_status              VARCHAR(255)    NOT NULL DEFAULT 'DRAFT',
    col_ocr_status          VARCHAR(255)    DEFAULT 'PENDING',
    col_publish_date        TIMESTAMPTZ,
    main_genre_id           BIGINT,
    sub_genre_id            BIGINT,

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_books_author_title UNIQUE (col_author_id, col_title),
    CONSTRAINT fk_books_author FOREIGN KEY (col_author_id) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_books_main_genre FOREIGN KEY (main_genre_id) REFERENCES tbl_main_genres(col_id),
    CONSTRAINT fk_books_sub_genre FOREIGN KEY (sub_genre_id) REFERENCES tbl_sub_genres(col_id),

    CONSTRAINT fk_books_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_books_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

CREATE TABLE IF NOT EXISTS tbl_book_pages (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_book_id             BIGINT          NOT NULL,
    col_page_number         INTEGER         NOT NULL,
    col_markdown_content    TEXT            NOT NULL,
    col_ocr_status          VARCHAR(255)    NOT NULL,
    col_error_message       VARCHAR(2000),
    col_word_count          INTEGER,

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_book_pages_book_page UNIQUE (col_book_id, col_page_number),
    CONSTRAINT fk_book_pages_book FOREIGN KEY (col_book_id) REFERENCES tbl_books(col_id),

    CONSTRAINT fk_book_pages_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_book_pages_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

CREATE INDEX IF NOT EXISTS idx_book_pages_book ON tbl_book_pages (col_book_id);
CREATE INDEX IF NOT EXISTS idx_book_pages_page ON tbl_book_pages (col_page_number);

CREATE TABLE IF NOT EXISTS tbl_book_library_entries (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_user_id             BIGINT          NOT NULL,
    col_book_id             BIGINT          NOT NULL,
    col_is_favorite         BOOLEAN         NOT NULL DEFAULT FALSE,
    col_added_at            TIMESTAMPTZ     NOT NULL,

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_book_library_entries_user_book UNIQUE (col_user_id, col_book_id),
    CONSTRAINT fk_book_library_entries_user FOREIGN KEY (col_user_id) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_book_library_entries_book FOREIGN KEY (col_book_id) REFERENCES tbl_books(col_id),

    CONSTRAINT fk_book_library_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_book_library_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

CREATE TABLE IF NOT EXISTS tbl_book_reviews (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_book_id             BIGINT          NOT NULL,
    col_reader_id           BIGINT          NOT NULL,
    col_rating              INTEGER         NOT NULL,
    col_comment             TEXT,

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_book_reviews_reader_book UNIQUE (col_book_id, col_reader_id),
    CONSTRAINT fk_book_reviews_book FOREIGN KEY (col_book_id) REFERENCES tbl_books(col_id),
    CONSTRAINT fk_book_reviews_reader FOREIGN KEY (col_reader_id) REFERENCES tbl_users(col_id),

    CONSTRAINT fk_book_reviews_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_book_reviews_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

-- ============================================================================
-- 4. ATTACHMENTS
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_attachments (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_file_name           VARCHAR(255)    NOT NULL,
    col_storage_path        VARCHAR(512)    NOT NULL,
    col_user_id             BIGINT,
    col_entity_id           BIGINT          NOT NULL,
    col_entity_type         VARCHAR(50)     NOT NULL,
    col_attachment_type     VARCHAR(50),
    col_source_url          VARCHAR(512),
    col_mime_type           VARCHAR(100),
    col_file_size           BIGINT,

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT fk_attachments_user FOREIGN KEY (col_user_id) REFERENCES tbl_users(col_id),

    CONSTRAINT fk_attachments_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_attachments_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

-- ============================================================================
-- 5. OCR FAILURES
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_ocr_failures (
    col_id                  BIGSERIAL       PRIMARY KEY,
    book_id                 BIGINT          NOT NULL,
    page_number             INTEGER         NOT NULL,
    s3_key                  VARCHAR(512)    NOT NULL,
    error_message           TEXT,
    attempts                INTEGER         NOT NULL DEFAULT 0,

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uk_ocr_failure_book_page UNIQUE (book_id, page_number),
    CONSTRAINT fk_ocr_failures_book FOREIGN KEY (book_id) REFERENCES tbl_books(col_id),

    CONSTRAINT fk_ocr_failures_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_ocr_failures_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

CREATE INDEX IF NOT EXISTS idx_ocr_failures_book ON tbl_ocr_failures (book_id);
CREATE INDEX IF NOT EXISTS idx_ocr_failures_page ON tbl_ocr_failures (page_number);

-- ============================================================================
-- 6. INTERACTIVE STORYTELLING
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_stories (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_title               VARCHAR(255)    NOT NULL,
    col_genre               VARCHAR(255),
    col_story_lens          VARCHAR(255)    NOT NULL,
    col_scene_count         INTEGER         NOT NULL,
    col_visual_style        VARCHAR(255),
    col_visual_style_notes  TEXT,
    col_author_id           BIGINT          NOT NULL,

    -- StoryConstitution (embedded)
    setting_time            VARCHAR(255),
    setting_place           VARCHAR(255),
    core_theme              TEXT,
    tone                    TEXT,
    philosophy              TEXT,
    main_conflict           TEXT,
    forbidden_elements      TEXT,
    pacing                  VARCHAR(255),

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT fk_story_author FOREIGN KEY (col_author_id) REFERENCES tbl_users(col_id),

    CONSTRAINT fk_stories_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_stories_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

CREATE TABLE IF NOT EXISTS tbl_reading_sessions (
    col_id                          BIGSERIAL       PRIMARY KEY,
    col_story_id                    BIGINT          NOT NULL,
    col_reader_id                   BIGINT          NOT NULL,
    col_status                      VARCHAR(255)    NOT NULL,
    col_state_json                  TEXT            NOT NULL,
    col_rolling_summary             TEXT,
    col_last_summarized_turn_index  INTEGER         NOT NULL DEFAULT 0,

    -- Audit (embedded)
    col_created_by                  BIGINT,
    col_last_modified_by            BIGINT,
    created_at                      TIMESTAMPTZ,
    updated_at                      TIMESTAMPTZ,

    version                         INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT fk_reading_session_story FOREIGN KEY (col_story_id) REFERENCES tbl_stories(col_id),
    CONSTRAINT fk_reading_session_reader FOREIGN KEY (col_reader_id) REFERENCES tbl_users(col_id),

    CONSTRAINT fk_reading_sessions_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_reading_sessions_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

CREATE INDEX IF NOT EXISTS idx_reading_sessions_story_reader ON tbl_reading_sessions (col_story_id, col_reader_id);

CREATE TABLE IF NOT EXISTS tbl_turns (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_session_id          BIGINT          NOT NULL,
    col_turn_index          INTEGER         NOT NULL,
    col_scene_text          TEXT            NOT NULL,
    col_choices_json        TEXT            NOT NULL,
    col_chosen_choice_id    VARCHAR(255),
    col_is_summarized       BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_turns_session_turn_index UNIQUE (col_session_id, col_turn_index),
    CONSTRAINT fk_turn_session FOREIGN KEY (col_session_id) REFERENCES tbl_reading_sessions(col_id),

    CONSTRAINT fk_turns_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_turns_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

CREATE INDEX IF NOT EXISTS idx_turns_session_turn_index ON tbl_turns (col_session_id, col_turn_index);
