-- A book may have more than one extra character (a grandparent, a friend). The original rule allowed exactly one row per kind,
-- so a second "companion" was rejected. CHILD and COMPANION stay unique per book; SUPPORTING rows are unlimited here
-- (the application caps the total number of characters) and are told apart by col_character_id.
ALTER TABLE tbl_storybook_characters DROP CONSTRAINT IF EXISTS uq_sb_character_kind;

CREATE UNIQUE INDEX IF NOT EXISTS uq_sb_character_main_kinds
    ON tbl_storybook_characters (col_storybook_id, col_kind)
    WHERE col_kind IN ('CHILD', 'COMPANION');

CREATE UNIQUE INDEX IF NOT EXISTS uq_sb_character_supporting_id
    ON tbl_storybook_characters (col_storybook_id, col_character_id)
    WHERE col_kind = 'SUPPORTING';
