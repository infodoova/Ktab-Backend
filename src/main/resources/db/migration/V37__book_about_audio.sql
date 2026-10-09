-- ============================================================================
-- Flyway Migration V37: the short "about the book" audio of a book
--
-- An audio that someone recorded (it is uploaded, never generated) to introduce a book, with a text description. The file
-- itself lives in object storage (col_storage_path); this table keeps where it is and what it is. A book has at most one,
-- so the book id is the key, and deleting the book deletes its row.
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_book_about_audios (
    col_book_id           BIGINT        PRIMARY KEY,
    col_storage_path      VARCHAR(512)  NOT NULL,
    col_file_name         VARCHAR(255)  NOT NULL,
    col_mime_type         VARCHAR(100)  NOT NULL,
    col_file_size         BIGINT        NOT NULL,
    col_duration_seconds  INTEGER,
    col_description       TEXT,
    col_created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    col_updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_book_about_audios_book FOREIGN KEY (col_book_id) REFERENCES tbl_books (col_id) ON DELETE CASCADE
);
