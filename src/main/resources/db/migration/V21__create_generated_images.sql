-- ============================================================================
-- Flyway Migration V21: Image Content Generator Feature (Cloudflare R2 + DB)
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS tbl_generated_images (
    col_id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    col_book_id             BIGINT          NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_user_id             BIGINT          NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    
    -- User Creative Input
    col_user_context        TEXT            NOT NULL,
    col_theme               VARCHAR(50)     NOT NULL,
    col_aspect_ratio        VARCHAR(20)     NOT NULL,
    col_style_notes         VARCHAR(500),
    
    -- AI Generation Metadata
    col_prompt_hash         VARCHAR(64)     NOT NULL,
    col_ai_model            VARCHAR(100)    NOT NULL DEFAULT 'gemini-2.5-flash-image',
    
    -- Cloudflare R2 Storage Metadata
    col_storage_key         VARCHAR(512),
    col_cf_public_url       VARCHAR(1024),
    col_mime_type           VARCHAR(50)     DEFAULT 'image/png',
    col_file_size_bytes     BIGINT,
    
    -- Status & Lifecycle
    col_status              VARCHAR(20)     NOT NULL DEFAULT 'QUEUED',
    col_failure_reason      TEXT,
    col_retry_count         INTEGER         NOT NULL DEFAULT 0,
    col_completed_at        TIMESTAMPTZ,
    
    -- BaseUuidEntity Audit fields
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 INTEGER         NOT NULL DEFAULT 0,
    
    CONSTRAINT chk_generated_image_status CHECK (col_status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_generated_image_aspect_ratio CHECK (col_aspect_ratio IN ('1:1', '3:4', '4:3', '9:16', '16:9', '2:3', '3:2'))
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_generated_images_book_id
    ON tbl_generated_images (col_book_id);

CREATE INDEX IF NOT EXISTS idx_generated_images_user_id
    ON tbl_generated_images (col_user_id);

CREATE INDEX IF NOT EXISTS idx_generated_images_status
    ON tbl_generated_images (col_status) WHERE col_status IN ('QUEUED', 'PROCESSING');

CREATE INDEX IF NOT EXISTS idx_generated_images_user_book
    ON tbl_generated_images (col_book_id, col_user_id, created_at DESC);

-- Unique constraint for active in-flight duplicate prevention
CREATE UNIQUE INDEX IF NOT EXISTS uq_generated_images_in_flight
    ON tbl_generated_images (col_user_id, col_book_id, col_prompt_hash)
    WHERE col_status IN ('QUEUED', 'PROCESSING');
