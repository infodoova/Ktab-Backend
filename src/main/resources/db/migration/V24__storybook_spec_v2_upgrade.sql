-- ============================================================================
-- Flyway Migration V24: Storybook V2 Upgrade (Dynamic narrative, Multi-character, 15-20 pages)
-- ============================================================================

-- 1. Expand page count constraint to support 15 to 20 pages (default 18)
ALTER TABLE tbl_storybooks DROP CONSTRAINT IF EXISTS tbl_storybooks_col_page_count_check;
ALTER TABLE tbl_storybooks ADD CONSTRAINT tbl_storybooks_col_page_count_check CHECK (col_page_count BETWEEN 10 AND 20);

-- 2. Add narrative steering & intermediate AI artifact columns to tbl_storybooks
ALTER TABLE tbl_storybooks ADD COLUMN IF NOT EXISTS col_theme VARCHAR(128);
ALTER TABLE tbl_storybooks ADD COLUMN IF NOT EXISTS col_story_tone VARCHAR(64);
ALTER TABLE tbl_storybooks ADD COLUMN IF NOT EXISTS col_lesson VARCHAR(128);
ALTER TABLE tbl_storybooks ADD COLUMN IF NOT EXISTS col_story_idea TEXT;
ALTER TABLE tbl_storybooks ADD COLUMN IF NOT EXISTS col_things_to_avoid JSONB;
ALTER TABLE tbl_storybooks ADD COLUMN IF NOT EXISTS col_orientation VARCHAR(32) NOT NULL DEFAULT 'PORTRAIT';
ALTER TABLE tbl_storybooks ADD COLUMN IF NOT EXISTS col_character_bible JSONB;
ALTER TABLE tbl_storybooks ADD COLUMN IF NOT EXISTS col_story_blueprint JSONB;
ALTER TABLE tbl_storybooks ADD COLUMN IF NOT EXISTS col_style_bible JSONB;
ALTER TABLE tbl_storybooks ADD COLUMN IF NOT EXISTS col_language_ruleset VARCHAR(64);

-- 3. Add rich character metadata to tbl_storybook_characters
ALTER TABLE tbl_storybook_characters ADD COLUMN IF NOT EXISTS col_character_id VARCHAR(64);
ALTER TABLE tbl_storybook_characters ADD COLUMN IF NOT EXISTS col_character_type VARCHAR(32) NOT NULL DEFAULT 'HUMAN';
ALTER TABLE tbl_storybook_characters ADD COLUMN IF NOT EXISTS col_role VARCHAR(32) NOT NULL DEFAULT 'MAIN';
ALTER TABLE tbl_storybook_characters ADD COLUMN IF NOT EXISTS col_relationship VARCHAR(128);
ALTER TABLE tbl_storybook_characters ADD COLUMN IF NOT EXISTS col_clothing TEXT;
ALTER TABLE tbl_storybook_characters ADD COLUMN IF NOT EXISTS col_personality JSONB;
ALTER TABLE tbl_storybook_characters ADD COLUMN IF NOT EXISTS col_advanced_details JSONB;
ALTER TABLE tbl_storybook_characters ADD COLUMN IF NOT EXISTS col_master_sheet_key TEXT;
