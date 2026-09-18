-- ============================================================================
-- Flyway Migration V5
-- Search Optimization Indexes & Arabic Normalization Support
-- ============================================================================

-- 1. Try enabling pg_trgm extension safely for high-performance fuzzy/trigram search
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_trgm extension could not be enabled, falling back to standard B-Tree indexes: %', SQLERRM;
END $$;

-- 2. Performance indexes on tbl_books
CREATE INDEX IF NOT EXISTS idx_books_status_publish ON tbl_books(col_status, col_publish_date DESC);
CREATE INDEX IF NOT EXISTS idx_books_rating ON tbl_books(col_average_rating DESC);
CREATE INDEX IF NOT EXISTS idx_books_main_genre ON tbl_books(main_genre_id);
CREATE INDEX IF NOT EXISTS idx_books_sub_genre ON tbl_books(sub_genre_id);
CREATE INDEX IF NOT EXISTS idx_books_author_status ON tbl_books(col_author_id, col_status);
CREATE INDEX IF NOT EXISTS idx_books_age_range ON tbl_books(col_age_range_min, col_age_range_max);

-- 3. GIN Trigram index on tbl_books title & custom author name if pg_trgm is present
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        CREATE INDEX IF NOT EXISTS idx_books_title_trgm ON tbl_books USING gin (col_title gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS idx_books_custom_author_trgm ON tbl_books USING gin (col_custom_author_name gin_trgm_ops);
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Could not create trigram indexes on tbl_books: %', SQLERRM;
END $$;

-- 4. In-book page search indexes
CREATE INDEX IF NOT EXISTS idx_book_pages_ocr_status ON tbl_book_pages(col_ocr_status);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        CREATE INDEX IF NOT EXISTS idx_book_pages_content_trgm ON tbl_book_pages USING gin (col_markdown_content gin_trgm_ops);
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Could not create trigram index on tbl_book_pages: %', SQLERRM;
END $$;

-- 5. Story and Review search indexes
CREATE INDEX IF NOT EXISTS idx_stories_genre ON tbl_stories(col_genre);
CREATE INDEX IF NOT EXISTS idx_stories_author ON tbl_stories(col_author_id);
CREATE INDEX IF NOT EXISTS idx_reviews_book_rating ON tbl_book_reviews(col_book_id, col_rating);
CREATE INDEX IF NOT EXISTS idx_orgs_city_country ON tbl_library_organizations(col_city, col_country);
