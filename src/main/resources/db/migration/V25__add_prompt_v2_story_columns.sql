-- V25: Add Story Engine v2 columns for story_bible, storyboard, image_brief, and state tracking

ALTER TABLE tbl_stories
    ADD COLUMN IF NOT EXISTS col_story_bible TEXT;

ALTER TABLE tbl_reading_sessions
    ADD COLUMN IF NOT EXISTS col_current_risk_mapping VARCHAR(255),
    ADD COLUMN IF NOT EXISTS col_open_threads TEXT,
    ADD COLUMN IF NOT EXISTS col_unpaid_setups TEXT,
    ADD COLUMN IF NOT EXISTS col_inventory TEXT,
    ADD COLUMN IF NOT EXISTS col_character_status TEXT;

ALTER TABLE tbl_turns
    ADD COLUMN IF NOT EXISTS col_storyboard TEXT,
    ADD COLUMN IF NOT EXISTS col_image_brief TEXT,
    ADD COLUMN IF NOT EXISTS col_choices_meta_json TEXT;
