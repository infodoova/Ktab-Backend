-- ============================================================================
-- Flyway Migration V10
-- Comprehensive Cascading Update & Delete Schema Alignment
-- Eliminates orphan records by ensuring all foreign keys have explicit
-- ON DELETE (CASCADE / SET NULL) AND ON UPDATE CASCADE actions.
-- ============================================================================

-- ============================================================================
-- SECTION A: Foreign keys referencing tbl_users (Domain / Business columns)
-- ============================================================================

-- 1. tbl_user_codes -> tbl_users (CASCADE: verification codes belong to user)
ALTER TABLE tbl_user_codes DROP CONSTRAINT IF EXISTS fk_user_codes_user;
ALTER TABLE tbl_user_codes
    ADD CONSTRAINT fk_user_codes_user
    FOREIGN KEY (col_user_id) REFERENCES tbl_users(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 2. tbl_user_settings -> tbl_users (CASCADE: 1:1 settings belong to user)
ALTER TABLE tbl_user_settings DROP CONSTRAINT IF EXISTS fk_user_settings_user;
ALTER TABLE tbl_user_settings
    ADD CONSTRAINT fk_user_settings_user
    FOREIGN KEY (col_user_id) REFERENCES tbl_users(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 3. tbl_refresh_tokens -> tbl_users (CASCADE: auth sessions belong to user)
ALTER TABLE tbl_refresh_tokens DROP CONSTRAINT IF EXISTS fk_refresh_tokens_user;
ALTER TABLE tbl_refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (col_user_id) REFERENCES tbl_users(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 4. tbl_books.col_author_id -> tbl_users (SET NULL: preserve book, unassign author account)
ALTER TABLE tbl_books DROP CONSTRAINT IF EXISTS fk_books_author;
ALTER TABLE tbl_books
    ADD CONSTRAINT fk_books_author
    FOREIGN KEY (col_author_id) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 5. tbl_books.col_uploader_id -> tbl_users (SET NULL: preserve book, unassign uploader)
ALTER TABLE tbl_books DROP CONSTRAINT IF EXISTS fk_books_uploader;
ALTER TABLE tbl_books
    ADD CONSTRAINT fk_books_uploader
    FOREIGN KEY (col_uploader_id) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 6. tbl_books.col_reviewed_by -> tbl_users (SET NULL: preserve book, unassign reviewer)
ALTER TABLE tbl_books DROP CONSTRAINT IF EXISTS fk_books_reviewed_by;
ALTER TABLE tbl_books
    ADD CONSTRAINT fk_books_reviewed_by
    FOREIGN KEY (col_reviewed_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 7. tbl_book_library_entries -> tbl_users (CASCADE: reader's personal shelf entry)
ALTER TABLE tbl_book_library_entries DROP CONSTRAINT IF EXISTS fk_book_library_entries_user;
ALTER TABLE tbl_book_library_entries
    ADD CONSTRAINT fk_book_library_entries_user
    FOREIGN KEY (col_user_id) REFERENCES tbl_users(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 8. tbl_book_reviews -> tbl_users (CASCADE: review authored by user)
ALTER TABLE tbl_book_reviews DROP CONSTRAINT IF EXISTS fk_book_reviews_reader;
ALTER TABLE tbl_book_reviews
    ADD CONSTRAINT fk_book_reviews_reader
    FOREIGN KEY (col_reader_id) REFERENCES tbl_users(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 9. tbl_attachments.col_user_id -> tbl_users (SET NULL: preserve attachment, unassign user)
ALTER TABLE tbl_attachments DROP CONSTRAINT IF EXISTS fk_attachments_user;
ALTER TABLE tbl_attachments
    ADD CONSTRAINT fk_attachments_user
    FOREIGN KEY (col_user_id) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 10. tbl_stories.col_author_id -> tbl_users (CASCADE: user's authored stories)
ALTER TABLE tbl_stories DROP CONSTRAINT IF EXISTS fk_story_author;
ALTER TABLE tbl_stories
    ADD CONSTRAINT fk_story_author
    FOREIGN KEY (col_author_id) REFERENCES tbl_users(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 11. tbl_reading_sessions.col_reader_id -> tbl_users (CASCADE: reader's interactive sessions)
ALTER TABLE tbl_reading_sessions DROP CONSTRAINT IF EXISTS fk_reading_session_reader;
ALTER TABLE tbl_reading_sessions
    ADD CONSTRAINT fk_reading_session_reader
    FOREIGN KEY (col_reader_id) REFERENCES tbl_users(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- ============================================================================
-- SECTION B: Foreign keys referencing tbl_books
-- ============================================================================

-- 12. tbl_book_pages -> tbl_books (CASCADE: pages belong to book)
ALTER TABLE tbl_book_pages DROP CONSTRAINT IF EXISTS fk_book_pages_book;
ALTER TABLE tbl_book_pages
    ADD CONSTRAINT fk_book_pages_book
    FOREIGN KEY (col_book_id) REFERENCES tbl_books(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 13. tbl_book_sections -> tbl_books (CASCADE: TOC sections belong to book)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT constraint_name
        FROM information_schema.table_constraints
        WHERE table_name = 'tbl_book_sections'
          AND constraint_type = 'FOREIGN KEY'
          AND constraint_name LIKE '%book%'
    ) LOOP
        EXECUTE 'ALTER TABLE tbl_book_sections DROP CONSTRAINT IF EXISTS ' || quote_ident(r.constraint_name);
    END LOOP;
END $$;
ALTER TABLE tbl_book_sections DROP CONSTRAINT IF EXISTS fk_book_sections_book;
ALTER TABLE tbl_book_sections
    ADD CONSTRAINT fk_book_sections_book
    FOREIGN KEY (col_book_id) REFERENCES tbl_books(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 14. tbl_book_library_entries -> tbl_books (CASCADE: shelf entry meaningless without book)
ALTER TABLE tbl_book_library_entries DROP CONSTRAINT IF EXISTS fk_book_library_entries_book;
ALTER TABLE tbl_book_library_entries
    ADD CONSTRAINT fk_book_library_entries_book
    FOREIGN KEY (col_book_id) REFERENCES tbl_books(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 15. tbl_book_reviews -> tbl_books (CASCADE: review meaningless without book)
ALTER TABLE tbl_book_reviews DROP CONSTRAINT IF EXISTS fk_book_reviews_book;
ALTER TABLE tbl_book_reviews
    ADD CONSTRAINT fk_book_reviews_book
    FOREIGN KEY (col_book_id) REFERENCES tbl_books(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 16. tbl_ocr_failures -> tbl_books (CASCADE: failure logs belong to book)
ALTER TABLE tbl_ocr_failures DROP CONSTRAINT IF EXISTS fk_ocr_failures_book;
ALTER TABLE tbl_ocr_failures
    ADD CONSTRAINT fk_ocr_failures_book
    FOREIGN KEY (book_id) REFERENCES tbl_books(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- ============================================================================
-- SECTION C: Hierarchical & Structure foreign keys
-- ============================================================================

-- 17. tbl_book_sections -> tbl_book_sections (CASCADE: child sections follow parent)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT constraint_name
        FROM information_schema.table_constraints
        WHERE table_name = 'tbl_book_sections'
          AND constraint_type = 'FOREIGN KEY'
          AND constraint_name LIKE '%parent%'
    ) LOOP
        EXECUTE 'ALTER TABLE tbl_book_sections DROP CONSTRAINT IF EXISTS ' || quote_ident(r.constraint_name);
    END LOOP;
END $$;
ALTER TABLE tbl_book_sections DROP CONSTRAINT IF EXISTS fk_book_sections_parent;
ALTER TABLE tbl_book_sections
    ADD CONSTRAINT fk_book_sections_parent
    FOREIGN KEY (col_parent_id) REFERENCES tbl_book_sections(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 18. tbl_book_pages -> tbl_book_sections (SET NULL: unassign section, preserve page)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT constraint_name
        FROM information_schema.table_constraints
        WHERE table_name = 'tbl_book_pages'
          AND constraint_type = 'FOREIGN KEY'
          AND constraint_name LIKE '%section%'
    ) LOOP
        EXECUTE 'ALTER TABLE tbl_book_pages DROP CONSTRAINT IF EXISTS ' || quote_ident(r.constraint_name);
    END LOOP;
END $$;
ALTER TABLE tbl_book_pages DROP CONSTRAINT IF EXISTS fk_book_pages_section;
ALTER TABLE tbl_book_pages
    ADD CONSTRAINT fk_book_pages_section
    FOREIGN KEY (col_section_id) REFERENCES tbl_book_sections(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 19. tbl_reading_sessions -> tbl_stories (CASCADE: reading sessions belong to story)
ALTER TABLE tbl_reading_sessions DROP CONSTRAINT IF EXISTS fk_reading_session_story;
ALTER TABLE tbl_reading_sessions
    ADD CONSTRAINT fk_reading_session_story
    FOREIGN KEY (col_story_id) REFERENCES tbl_stories(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 20. tbl_turns -> tbl_reading_sessions (CASCADE: turns belong to session)
ALTER TABLE tbl_turns DROP CONSTRAINT IF EXISTS fk_turn_session;
ALTER TABLE tbl_turns
    ADD CONSTRAINT fk_turn_session
    FOREIGN KEY (col_session_id) REFERENCES tbl_reading_sessions(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- ============================================================================
-- SECTION D: Genre hierarchy foreign keys
-- ============================================================================

-- 21. tbl_sub_genres -> tbl_main_genres (CASCADE: sub-genres belong to main genre)
ALTER TABLE tbl_sub_genres DROP CONSTRAINT IF EXISTS fk_sub_genres_main_genre;
ALTER TABLE tbl_sub_genres
    ADD CONSTRAINT fk_sub_genres_main_genre
    FOREIGN KEY (main_genre_id) REFERENCES tbl_main_genres(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- 22. tbl_books -> tbl_main_genres (SET NULL: preserve book, unassign genre)
ALTER TABLE tbl_books DROP CONSTRAINT IF EXISTS fk_books_main_genre;
ALTER TABLE tbl_books
    ADD CONSTRAINT fk_books_main_genre
    FOREIGN KEY (main_genre_id) REFERENCES tbl_main_genres(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 23. tbl_books -> tbl_sub_genres (SET NULL: preserve book, unassign sub-genre)
ALTER TABLE tbl_books DROP CONSTRAINT IF EXISTS fk_books_sub_genre;
ALTER TABLE tbl_books
    ADD CONSTRAINT fk_books_sub_genre
    FOREIGN KEY (sub_genre_id) REFERENCES tbl_sub_genres(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- ============================================================================
-- SECTION E: Library Organization foreign keys
-- ============================================================================

-- 24. tbl_users -> tbl_library_organizations (SET NULL: preserve staff, unassign org)
ALTER TABLE tbl_users DROP CONSTRAINT IF EXISTS fk_users_library_org;
ALTER TABLE tbl_users
    ADD CONSTRAINT fk_users_library_org
    FOREIGN KEY (col_library_organization_id) REFERENCES tbl_library_organizations(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 25. tbl_books -> tbl_library_organizations (CASCADE: org books belong to org)
ALTER TABLE tbl_books DROP CONSTRAINT IF EXISTS fk_books_library_org;
ALTER TABLE tbl_books
    ADD CONSTRAINT fk_books_library_org
    FOREIGN KEY (col_library_organization_id) REFERENCES tbl_library_organizations(col_id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- ============================================================================
-- SECTION F: Audit column foreign keys (All ON DELETE SET NULL ON UPDATE CASCADE)
-- ============================================================================

-- 26. tbl_users audit FKs
ALTER TABLE tbl_users DROP CONSTRAINT IF EXISTS fk_users_created_by;
ALTER TABLE tbl_users
    ADD CONSTRAINT fk_users_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_users DROP CONSTRAINT IF EXISTS fk_users_last_modified_by;
ALTER TABLE tbl_users
    ADD CONSTRAINT fk_users_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 27. tbl_main_genres audit FKs
ALTER TABLE tbl_main_genres DROP CONSTRAINT IF EXISTS fk_main_genres_created_by;
ALTER TABLE tbl_main_genres
    ADD CONSTRAINT fk_main_genres_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_main_genres DROP CONSTRAINT IF EXISTS fk_main_genres_last_modified_by;
ALTER TABLE tbl_main_genres
    ADD CONSTRAINT fk_main_genres_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 28. tbl_sub_genres audit FKs
ALTER TABLE tbl_sub_genres DROP CONSTRAINT IF EXISTS fk_sub_genres_created_by;
ALTER TABLE tbl_sub_genres
    ADD CONSTRAINT fk_sub_genres_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_sub_genres DROP CONSTRAINT IF EXISTS fk_sub_genres_last_modified_by;
ALTER TABLE tbl_sub_genres
    ADD CONSTRAINT fk_sub_genres_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 29. tbl_books audit FKs
ALTER TABLE tbl_books DROP CONSTRAINT IF EXISTS fk_books_created_by;
ALTER TABLE tbl_books
    ADD CONSTRAINT fk_books_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_books DROP CONSTRAINT IF EXISTS fk_books_last_modified_by;
ALTER TABLE tbl_books
    ADD CONSTRAINT fk_books_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 30. tbl_book_pages audit FKs
ALTER TABLE tbl_book_pages DROP CONSTRAINT IF EXISTS fk_book_pages_created_by;
ALTER TABLE tbl_book_pages
    ADD CONSTRAINT fk_book_pages_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_book_pages DROP CONSTRAINT IF EXISTS fk_book_pages_last_modified_by;
ALTER TABLE tbl_book_pages
    ADD CONSTRAINT fk_book_pages_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 31. tbl_book_library_entries audit FKs
ALTER TABLE tbl_book_library_entries DROP CONSTRAINT IF EXISTS fk_book_library_created_by;
ALTER TABLE tbl_book_library_entries
    ADD CONSTRAINT fk_book_library_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_book_library_entries DROP CONSTRAINT IF EXISTS fk_book_library_last_modified_by;
ALTER TABLE tbl_book_library_entries
    ADD CONSTRAINT fk_book_library_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 32. tbl_book_reviews audit FKs
ALTER TABLE tbl_book_reviews DROP CONSTRAINT IF EXISTS fk_book_reviews_created_by;
ALTER TABLE tbl_book_reviews
    ADD CONSTRAINT fk_book_reviews_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_book_reviews DROP CONSTRAINT IF EXISTS fk_book_reviews_last_modified_by;
ALTER TABLE tbl_book_reviews
    ADD CONSTRAINT fk_book_reviews_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 33. tbl_attachments audit FKs
ALTER TABLE tbl_attachments DROP CONSTRAINT IF EXISTS fk_attachments_created_by;
ALTER TABLE tbl_attachments
    ADD CONSTRAINT fk_attachments_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_attachments DROP CONSTRAINT IF EXISTS fk_attachments_last_modified_by;
ALTER TABLE tbl_attachments
    ADD CONSTRAINT fk_attachments_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 34. tbl_ocr_failures audit FKs
ALTER TABLE tbl_ocr_failures DROP CONSTRAINT IF EXISTS fk_ocr_failures_created_by;
ALTER TABLE tbl_ocr_failures
    ADD CONSTRAINT fk_ocr_failures_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_ocr_failures DROP CONSTRAINT IF EXISTS fk_ocr_failures_last_modified_by;
ALTER TABLE tbl_ocr_failures
    ADD CONSTRAINT fk_ocr_failures_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 35. tbl_stories audit FKs
ALTER TABLE tbl_stories DROP CONSTRAINT IF EXISTS fk_stories_created_by;
ALTER TABLE tbl_stories
    ADD CONSTRAINT fk_stories_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_stories DROP CONSTRAINT IF EXISTS fk_stories_last_modified_by;
ALTER TABLE tbl_stories
    ADD CONSTRAINT fk_stories_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 36. tbl_reading_sessions audit FKs
ALTER TABLE tbl_reading_sessions DROP CONSTRAINT IF EXISTS fk_reading_sessions_created_by;
ALTER TABLE tbl_reading_sessions
    ADD CONSTRAINT fk_reading_sessions_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_reading_sessions DROP CONSTRAINT IF EXISTS fk_reading_sessions_last_modified_by;
ALTER TABLE tbl_reading_sessions
    ADD CONSTRAINT fk_reading_sessions_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 37. tbl_turns audit FKs
ALTER TABLE tbl_turns DROP CONSTRAINT IF EXISTS fk_turns_created_by;
ALTER TABLE tbl_turns
    ADD CONSTRAINT fk_turns_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_turns DROP CONSTRAINT IF EXISTS fk_turns_last_modified_by;
ALTER TABLE tbl_turns
    ADD CONSTRAINT fk_turns_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

-- 38. tbl_library_organizations audit FKs
ALTER TABLE tbl_library_organizations DROP CONSTRAINT IF EXISTS fk_library_org_created_by;
ALTER TABLE tbl_library_organizations
    ADD CONSTRAINT fk_library_org_created_by
    FOREIGN KEY (col_created_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE tbl_library_organizations DROP CONSTRAINT IF EXISTS fk_library_org_last_modified_by;
ALTER TABLE tbl_library_organizations
    ADD CONSTRAINT fk_library_org_last_modified_by
    FOREIGN KEY (col_last_modified_by) REFERENCES tbl_users(col_id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;
