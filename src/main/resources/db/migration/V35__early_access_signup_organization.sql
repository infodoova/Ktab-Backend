-- ============================================================================
-- Flyway Migration V35: the library organization an admin librarian signs up with
--
-- An admin librarian runs a library organization (tbl_library_organizations), so their signup carries that organization's
-- details with the same meaning and sizes as the columns of the real table. When the signup becomes active the
-- organization is created from these columns (col_org_name -> col_name, and so on; the slug is generated from the name
-- then, as for any new organization, and the status starts as ACTIVE). Only admin librarian signups (role 35) have them.
-- ============================================================================

ALTER TABLE tbl_early_access_signups
    ADD COLUMN IF NOT EXISTS col_org_name        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS col_org_description TEXT,
    ADD COLUMN IF NOT EXISTS col_org_city        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS col_org_country     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS col_org_address     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS col_org_website     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS col_org_email       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS col_org_phone       VARCHAR(50);

-- An admin librarian must name the organization; nobody else has organization details.
ALTER TABLE tbl_early_access_signups DROP CONSTRAINT IF EXISTS ck_early_access_org_for_admin_librarian;
ALTER TABLE tbl_early_access_signups
    ADD CONSTRAINT ck_early_access_org_for_admin_librarian CHECK (
        (col_role = '35' AND col_org_name IS NOT NULL)
        OR (col_role <> '35' AND col_org_name IS NULL AND col_org_description IS NULL AND col_org_city IS NULL
            AND col_org_country IS NULL AND col_org_address IS NULL AND col_org_website IS NULL
            AND col_org_email IS NULL AND col_org_phone IS NULL));
