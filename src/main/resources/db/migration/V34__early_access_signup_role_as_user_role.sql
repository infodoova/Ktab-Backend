-- ============================================================================
-- Flyway Migration V34: early-access roles use the same values as tbl_users.col_role
--
-- tbl_users keeps the role as a code (00 admin, 10 author, 20 reader, 30 librarian, 35 admin librarian, 40 publisher).
-- Signups store the same code, so turning a signup into a user is a plain copy of col_role. Only the three roles that
-- early access is offered for are allowed. V33 stored names, so its values are converted.
-- ============================================================================

ALTER TABLE tbl_early_access_signups DROP CONSTRAINT IF EXISTS ck_early_access_role;

UPDATE tbl_early_access_signups
SET col_role = CASE col_role
                   WHEN 'READER' THEN '20'
                   WHEN 'AUTHOR' THEN '10'
                   WHEN 'ADMIN_LIBRARIAN' THEN '35'
                   ELSE col_role
               END;

ALTER TABLE tbl_early_access_signups
    ADD CONSTRAINT ck_early_access_role CHECK (col_role IN ('20', '10', '35'));
