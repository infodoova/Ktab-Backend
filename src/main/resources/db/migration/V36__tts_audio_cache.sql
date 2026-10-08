-- ============================================================================
-- Flyway Migration V36: cache of generated reader TTS audio
--
-- The reader asks ElevenLabs to narrate a page of text. The same text, voice and model always produce equivalent audio,
-- so the MP3 is generated once and replayed from object storage afterwards. The MP3 itself lives in R2 (col_storage_key);
-- this table is the lookup index plus the word-timing alignment the reader needs to highlight text while it plays.
-- The key is a SHA-256 of everything that influences the audio (model, voice, output format, voice settings, text and
-- neighbouring text), so a change to any of them is a new row and nothing has to be invalidated.
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_tts_audio_cache (
    col_cache_key         VARCHAR(64)  PRIMARY KEY,
    col_storage_key       VARCHAR(512) NOT NULL,
    col_alignment_json    TEXT         NOT NULL,
    col_voice_id          VARCHAR(128) NOT NULL,
    col_model_id          VARCHAR(64)  NOT NULL,
    col_text_length       INTEGER      NOT NULL,
    col_audio_bytes       BIGINT       NOT NULL,
    col_hit_count         BIGINT       NOT NULL DEFAULT 0,
    col_created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    col_last_accessed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Lets a later cleanup job find the least recently used rows cheaply.
CREATE INDEX IF NOT EXISTS idx_tts_audio_cache_last_accessed ON tbl_tts_audio_cache (col_last_accessed_at);
