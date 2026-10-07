-- ============================================================================
-- Flyway Migration V33: classify early-access signups by the role the person wants
--
-- Early access is offered per role: readers, authors and admin librarians. V32 is already applied, so this adds the
-- column instead of changing that script.
-- ============================================================================

ALTER TABLE tbl_early_access_signups ADD COLUMN IF NOT EXISTS col_role VARCHAR(20);

-- Rows that exist before the role was asked for (none are expected) are treated as readers.
UPDATE tbl_early_access_signups SET col_role = 'READER' WHERE col_role IS NULL;

ALTER TABLE tbl_early_access_signups ALTER COLUMN col_role SET NOT NULL;
ALTER TABLE tbl_early_access_signups DROP CONSTRAINT IF EXISTS ck_early_access_role;
ALTER TABLE tbl_early_access_signups
    ADD CONSTRAINT ck_early_access_role CHECK (col_role IN ('READER', 'AUTHOR', 'ADMIN_LIBRARIAN'));

CREATE INDEX IF NOT EXISTS idx_early_access_role ON tbl_early_access_signups (col_role, col_early_access);
