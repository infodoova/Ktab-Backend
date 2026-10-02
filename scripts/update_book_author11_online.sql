-- ============================================================================
-- SQL Script: Update & Link 'بعض الصوت' PDF and Cover for Author 11 & Library
-- Target: Online / Production PostgreSQL Database
-- ============================================================================

BEGIN;

DO $$
DECLARE
    v_author_id     BIGINT := 11;
    v_book_id       BIGINT;
    v_lib_book_id   BIGINT;
    v_cover_path    VARCHAR := 'libraries/1/books/cover/61d6ef0b-f618-40e2-af50-0ff040ec3e37.jpg';
    v_pdf_path      VARCHAR := 'libraries/1/books/pdf/8b858af7-78e3-437c-9f1c-26356e4d69ed.pdf';
    v_cover_size    BIGINT  := 88501;
    v_pdf_size      BIGINT  := 1336182;
BEGIN
    -- ------------------------------------------------------------------------
    -- 1. Locate Library Book 'بعض الصوت'
    -- ------------------------------------------------------------------------
    SELECT col_id INTO v_lib_book_id
    FROM tbl_books
    WHERE col_title = 'بعض الصوت' AND col_book_source = 'LIBRARY'
    LIMIT 1;

    -- Update Library book's attachments if it exists
    IF v_lib_book_id IS NOT NULL THEN
        -- Library PDF attachment
        IF EXISTS (
            SELECT 1 FROM tbl_attachments
            WHERE col_entity_id = v_lib_book_id AND col_entity_type = 'Book' AND col_attachment_type = 'PDF_SOURCE'
        ) THEN
            UPDATE tbl_attachments
            SET col_storage_path = v_pdf_path,
                col_file_name = 'بعض الصوت.pdf',
                col_mime_type = 'application/pdf',
                col_file_size = v_pdf_size,
                updated_at = NOW()
            WHERE col_entity_id = v_lib_book_id AND col_entity_type = 'Book' AND col_attachment_type = 'PDF_SOURCE';
        ELSE
            INSERT INTO tbl_attachments (
                col_file_name, col_storage_path, col_entity_id, col_entity_type,
                col_attachment_type, col_mime_type, col_file_size, created_at, updated_at, version
            ) VALUES (
                'بعض الصوت.pdf', v_pdf_path, v_lib_book_id, 'Book',
                'PDF_SOURCE', 'application/pdf', v_pdf_size, NOW(), NOW(), 0
            );
        END IF;

        -- Library Cover attachment
        UPDATE tbl_attachments
        SET col_storage_path = v_cover_path,
            col_file_name = 'cover.jpg',
            col_mime_type = 'image/jpeg',
            col_file_size = v_cover_size,
            updated_at = NOW()
        WHERE col_entity_id = v_lib_book_id AND col_entity_type = 'Book' AND col_attachment_type = 'COVER_IMAGE';

        RAISE NOTICE 'Updated attachments for Library Book ID % ("بعض الصوت")', v_lib_book_id;
    END IF;

    -- ------------------------------------------------------------------------
    -- 2. Locate or Create Author Book for Author ID 11
    -- ------------------------------------------------------------------------
    SELECT col_id INTO v_book_id
    FROM tbl_books
    WHERE col_title = 'بعض الصوت' 
      AND (col_author_id = v_author_id OR (col_book_source = 'AUTHOR' AND col_uploader_id = v_author_id))
    LIMIT 1;

    IF v_book_id IS NULL THEN
        -- Insert new Author Book
        INSERT INTO tbl_books (
            col_title,
            col_description,
            col_custom_author_name,
            col_book_source,
            col_author_id,
            col_uploader_id,
            col_library_organization_id,
            col_language,
            col_age_range_min,
            col_age_range_max,
            col_page_count,
            col_has_audio,
            col_average_rating,
            col_total_reviews,
            col_status,
            col_ocr_status,
            col_publish_date,
            main_genre_id,
            sub_genre_id,
            created_at,
            updated_at,
            version
        ) VALUES (
            'بعض الصوت',
            'رواية تركية مترجمة تستكشف الوحدة والذاكرة والحب والعلاقات العائلية والضغوط الاجتماعية والاقتصادية، من خلال شخصيات تتقاطع حيواتهم بين الماضي والحاضر ومحاولات البحث عن معنى وانتماء.',
            'بيلغهان أوتشاك',
            'AUTHOR',
            v_author_id,
            v_author_id,
            NULL,
            'ar',
            16,
            99,
            256,
            FALSE,
            0.00,
            0,
            'PUBLISHED',
            'COMPLETED',
            NOW(),
            1,
            1,
            NOW(),
            NOW(),
            0
        ) RETURNING col_id INTO v_book_id;

        RAISE NOTICE 'Created Author Book ID % for Author %', v_book_id, v_author_id;
    ELSE
        UPDATE tbl_books
        SET col_author_id = v_author_id,
            col_uploader_id = v_author_id,
            col_book_source = 'AUTHOR',
            col_status = 'PUBLISHED',
            updated_at = NOW()
        WHERE col_id = v_book_id;

        RAISE NOTICE 'Found existing Author Book ID % for Author %', v_book_id, v_author_id;
    END IF;

    -- ------------------------------------------------------------------------
    -- 3. Link / Update Attachments for Author Book
    -- ------------------------------------------------------------------------
    -- COVER_IMAGE
    IF EXISTS (
        SELECT 1 FROM tbl_attachments
        WHERE col_entity_id = v_book_id AND col_entity_type = 'Book' AND col_attachment_type = 'COVER_IMAGE'
    ) THEN
        UPDATE tbl_attachments
        SET col_storage_path = v_cover_path,
            col_file_name    = 'cover.jpg',
            col_mime_type    = 'image/jpeg',
            col_file_size    = v_cover_size,
            col_user_id      = v_author_id,
            updated_at       = NOW()
        WHERE col_entity_id = v_book_id AND col_entity_type = 'Book' AND col_attachment_type = 'COVER_IMAGE';
    ELSE
        INSERT INTO tbl_attachments (
            col_file_name, col_storage_path, col_user_id, col_entity_id,
            col_entity_type, col_attachment_type, col_mime_type, col_file_size,
            col_created_by, created_at, updated_at, version
        ) VALUES (
            'cover.jpg', v_cover_path, v_author_id, v_book_id,
            'Book', 'COVER_IMAGE', 'image/jpeg', v_cover_size,
            v_author_id, NOW(), NOW(), 0
        );
    END IF;

    -- PDF_SOURCE
    IF EXISTS (
        SELECT 1 FROM tbl_attachments
        WHERE col_entity_id = v_book_id AND col_entity_type = 'Book' AND col_attachment_type = 'PDF_SOURCE'
    ) THEN
        UPDATE tbl_attachments
        SET col_storage_path = v_pdf_path,
            col_file_name    = 'بعض الصوت.pdf',
            col_mime_type    = 'application/pdf',
            col_file_size    = v_pdf_size,
            col_user_id      = v_author_id,
            updated_at       = NOW()
        WHERE col_entity_id = v_book_id AND col_entity_type = 'Book' AND col_attachment_type = 'PDF_SOURCE';
    ELSE
        INSERT INTO tbl_attachments (
            col_file_name, col_storage_path, col_user_id, col_entity_id,
            col_entity_type, col_attachment_type, col_mime_type, col_file_size,
            col_created_by, created_at, updated_at, version
        ) VALUES (
            'بعض الصوت.pdf', v_pdf_path, v_author_id, v_book_id,
            'Book', 'PDF_SOURCE', 'application/pdf', v_pdf_size,
            v_author_id, NOW(), NOW(), 0
        );
    END IF;

    -- ------------------------------------------------------------------------
    -- 4. Sync Sequences
    -- ------------------------------------------------------------------------
    PERFORM setval('tbl_books_col_id_seq', (SELECT COALESCE(MAX(col_id), 1) FROM tbl_books));
    PERFORM setval('tbl_attachments_col_id_seq', (SELECT COALESCE(MAX(col_id), 1) FROM tbl_attachments));

    RAISE NOTICE 'Successfully updated book and attachments for Author 11.';
END $$;

COMMIT;
