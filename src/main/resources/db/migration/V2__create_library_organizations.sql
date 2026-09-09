-- ============================================================================
-- Flyway Migration V2
-- Add Library Organization entity and multi-tenant library book support
-- ============================================================================

-- 1. Create tbl_library_organizations
CREATE TABLE IF NOT EXISTS tbl_library_organizations (
    col_id                  BIGSERIAL       PRIMARY KEY,
    col_name                VARCHAR(255)    NOT NULL,
    col_slug                VARCHAR(255)    NOT NULL,
    col_description         TEXT,
    col_logo_storage_path   VARCHAR(500),
    col_city                VARCHAR(100),
    col_country             VARCHAR(100),
    col_address             VARCHAR(255),
    col_website             VARCHAR(255),
    col_email               VARCHAR(255),
    col_phone               VARCHAR(50),
    col_status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',

    -- Audit (embedded)
    col_created_by          BIGINT,
    col_last_modified_by    BIGINT,
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,
    version                 INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT uq_library_org_slug UNIQUE (col_slug),
    CONSTRAINT fk_library_org_created_by FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id),
    CONSTRAINT fk_library_org_last_modified_by FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
);

-- 2. Link users to library organizations
ALTER TABLE tbl_users
    ADD COLUMN IF NOT EXISTS col_library_organization_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_users_library_org'
    ) THEN
        ALTER TABLE tbl_users
            ADD CONSTRAINT fk_users_library_org
            FOREIGN KEY (col_library_organization_id)
            REFERENCES tbl_library_organizations(col_id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_library_org_id ON tbl_users(col_library_organization_id);

-- 3. Enhance tbl_books with library organization tenancy and external author support
ALTER TABLE tbl_books
    ALTER COLUMN col_author_id DROP NOT NULL;

ALTER TABLE tbl_books
    ADD COLUMN IF NOT EXISTS col_book_source VARCHAR(20) NOT NULL DEFAULT 'AUTHOR',
    ADD COLUMN IF NOT EXISTS col_library_organization_id BIGINT,
    ADD COLUMN IF NOT EXISTS col_custom_author_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS col_uploader_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_books_library_org'
    ) THEN
        ALTER TABLE tbl_books
            ADD CONSTRAINT fk_books_library_org
            FOREIGN KEY (col_library_organization_id)
            REFERENCES tbl_library_organizations(col_id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_books_uploader'
    ) THEN
        ALTER TABLE tbl_books
            ADD CONSTRAINT fk_books_uploader
            FOREIGN KEY (col_uploader_id)
            REFERENCES tbl_users(col_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_books_library_org_id ON tbl_books(col_library_organization_id);
CREATE INDEX IF NOT EXISTS idx_books_book_source ON tbl_books(col_book_source);
