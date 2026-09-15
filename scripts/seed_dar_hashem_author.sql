-- ============================================================================
-- SQL Seed Script: Dar Hashem Author Setup & Attachment Re-linking
-- 
-- 1. Restores Ali Hashem (ali@darhashem.com) as ADMIN_LIBRARIAN (role: 35) for Dar Hashem.
-- 2. Creates/Updates Dar Hashem Author (author@darhashem.com) as AUTHOR (role: 10).
-- 3. Creates/Transfers 15 books under author@darhashem.com (col_book_source = 'AUTHOR').
-- 4. Links EXISTING attachments (covers and PDFs from Cloudflare / S3) 
--    to the author books WITHOUT creating new physical attachments or duplicate files.
--
-- Note: Default password for both users: Password123!
--       BCrypt Hash: $2a$10$TjIpHdJoqUd3qNfZn8095eGhTXEn3ZU3Nia2iVxM7cqgodt.EWKzy
-- ============================================================================

DO $$
DECLARE
    v_org_id        BIGINT;
    v_librarian_id  BIGINT;
    v_author_id     BIGINT;
    v_book_id       BIGINT;
    v_books_created INT := 0;
    v_books_updated INT := 0;
    v_atts_linked   INT := 0;
    r_book          RECORD;
    r_att           RECORD;
BEGIN
    -- ------------------------------------------------------------------------
    -- 1. Retrieve Library Organization: Dar Hashem
    -- ------------------------------------------------------------------------
    SELECT col_id INTO v_org_id 
    FROM tbl_library_organizations 
    WHERE col_slug = 'dar-hashem' OR col_name = 'Dar Hashem' 
    LIMIT 1;

    IF v_org_id IS NULL THEN
        v_org_id := 1;
    END IF;

    -- ------------------------------------------------------------------------
    -- 2. Restore Ali Hashem as ADMIN_LIBRARIAN (Role: 35, Active: 1)
    -- ------------------------------------------------------------------------
    INSERT INTO tbl_users (
        col_email,
        col_first_name,
        col_last_name,
        col_password_digest,
        col_role,
        col_is_active,
        col_library_organization_id,
        created_at,
        updated_at,
        version
    ) VALUES (
        'ali@darhashem.com',
        'Ali',
        'Hashem',
        '$2a$10$TjIpHdJoqUd3qNfZn8095eGhTXEn3ZU3Nia2iVxM7cqgodt.EWKzy', -- Password123!
        '35',                                                           -- ADMIN_LIBRARIAN
        '1',                                                            -- ACTIVE
        v_org_id,
        NOW(),
        NOW(),
        0
    )
    ON CONFLICT (col_email) DO UPDATE
        SET col_role = '35',
            col_is_active = '1',
            col_library_organization_id = v_org_id,
            updated_at = NOW()
    RETURNING col_id INTO v_librarian_id;

    RAISE NOTICE '=======================================================';
    RAISE NOTICE 'Admin Librarian: Ali Hashem (ali@darhashem.com) ID: %, Role: 35 (ADMIN_LIBRARIAN)', v_librarian_id;

    -- ------------------------------------------------------------------------
    -- 3. Create or Setup Author: author@darhashem.com (Role: 10 = AUTHOR)
    -- ------------------------------------------------------------------------
    INSERT INTO tbl_users (
        col_email,
        col_first_name,
        col_last_name,
        col_password_digest,
        col_role,
        col_is_active,
        col_library_organization_id,
        created_at,
        updated_at,
        version
    ) VALUES (
        'author@darhashem.com',
        'Dar Hashem',
        'Author',
        '$2a$10$TjIpHdJoqUd3qNfZn8095eGhTXEn3ZU3Nia2iVxM7cqgodt.EWKzy', -- Password123!
        '10',                                                           -- AUTHOR
        '1',                                                            -- ACTIVE
        NULL,                                                           -- Independent Author
        NOW(),
        NOW(),
        0
    )
    ON CONFLICT (col_email) DO UPDATE
        SET col_role = '10',
            col_is_active = '1',
            col_library_organization_id = NULL,
            updated_at = NOW()
    RETURNING col_id INTO v_author_id;

    RAISE NOTICE 'Author Account : Dar Hashem Author (author@darhashem.com) ID: %, Role: 10 (AUTHOR)', v_author_id;
    RAISE NOTICE '=======================================================';

    -- ------------------------------------------------------------------------
    -- 4. Process Books: Find existing Library books & link to author@darhashem.com
    -- ------------------------------------------------------------------------
    FOR r_book IN (
        SELECT b.col_id AS lib_book_id,
               b.col_title,
               b.col_description,
               b.col_custom_author_name,
               b.col_language,
               b.col_age_range_min,
               b.col_age_range_max,
               b.col_page_count,
               b.col_has_audio,
               b.main_genre_id,
               b.sub_genre_id
        FROM tbl_books b
        WHERE b.col_book_source = 'LIBRARY'
          AND b.col_title IN (
              'سيرة ملك: عبد الله الثاني... من واشنطن إلى عمّان',
              'صمود الدبلوماسية: مذكرات ثماني سنوات في وزارة الخارجية',
              'عندما ينام العالم: قصص، كلمات، وجروح فلسطينية مفتوحة',
              'عيون غزة: يوميات صمود',
              'قوة التفاوض: مبادئ وقواعد المفاوضات السياسية والدبلوماسية',
              'كنعان مكية: سيرة على تخوم العراق',
              'لظى: حكاية حرب لم تنته',
              'The Audacity of Resilience: An Insider’s View into the Making of Iranian Foreign Policy',
              'استراتيجية إيران الكبرى: تاريخ سياسي',
              'الرسائل المصرية: 24 كاتبًا عربيًا يروون أدوار «المحروسة» في تأهيل عرب القرن العشرين',
              'الزلزال: النظام العالمي بين الشعبوية والديمقراطية',
              'الصين والولايات المتحدة: حتمية الحرب الاقتصادية',
              'أميركا وإيران: الرسائل والمدافع – 300 عام من العلاقات المعقّدة',
              'بعض الصوت',
              'ثورة دونالد ترامب: قواعد القوى العظمى'
          )
        ORDER BY b.col_id
    ) LOOP
        -- Check if an author book with this title already exists (whether owned by old ali or new author)
        SELECT col_id INTO v_book_id
        FROM tbl_books
        WHERE col_book_source = 'AUTHOR'
          AND col_title = r_book.col_title
        LIMIT 1;

        IF v_book_id IS NOT NULL THEN
            -- Update ownership to author@darhashem.com
            UPDATE tbl_books
            SET col_author_id = v_author_id,
                col_uploader_id = v_author_id,
                col_library_organization_id = NULL,
                updated_at = NOW()
            WHERE col_id = v_book_id;

            -- Update attachment ownership
            UPDATE tbl_attachments
            SET col_user_id = v_author_id,
                col_created_by = v_author_id,
                updated_at = NOW()
            WHERE col_entity_id = v_book_id
              AND col_entity_type = 'Book';

            v_books_updated := v_books_updated + 1;
            RAISE NOTICE '  [↺] Re-assigned Author Book ID % to author@darhashem.com: "%"', v_book_id, r_book.col_title;
        ELSE
            -- Create new author book
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
                r_book.col_title,
                r_book.col_description,
                r_book.col_custom_author_name,
                'AUTHOR',
                v_author_id,
                v_author_id,
                NULL,
                r_book.col_language,
                r_book.col_age_range_min,
                r_book.col_age_range_max,
                r_book.col_page_count,
                r_book.col_has_audio,
                0.00,
                0,
                'PUBLISHED',
                'COMPLETED',
                NOW(),
                r_book.main_genre_id,
                r_book.sub_genre_id,
                NOW(),
                NOW(),
                0
            ) RETURNING col_id INTO v_book_id;

            v_books_created := v_books_created + 1;
            RAISE NOTICE '  [+] Created Author Book ID %: "%"', v_book_id, r_book.col_title;
        END IF;

        -- --------------------------------------------------------------------
        -- 5. Link existing attachments from library book to author book
        -- --------------------------------------------------------------------
        FOR r_att IN (
            SELECT col_file_name,
                   col_storage_path,
                   col_attachment_type,
                   col_mime_type,
                   col_file_size
            FROM tbl_attachments
            WHERE col_entity_id = r_book.lib_book_id
              AND col_entity_type = 'Book'
        ) LOOP
            IF NOT EXISTS (
                SELECT 1 FROM tbl_attachments
                WHERE col_entity_id = v_book_id
                  AND col_entity_type = 'Book'
                  AND col_attachment_type = r_att.col_attachment_type
            ) THEN
                INSERT INTO tbl_attachments (
                    col_file_name,
                    col_storage_path,
                    col_user_id,
                    col_entity_id,
                    col_entity_type,
                    col_attachment_type,
                    col_source_url,
                    col_mime_type,
                    col_file_size,
                    col_created_by,
                    created_at,
                    updated_at,
                    version
                ) VALUES (
                    r_att.col_file_name,
                    r_att.col_storage_path,
                    v_author_id,
                    v_book_id,
                    'Book',
                    r_att.col_attachment_type,
                    NULL,
                    r_att.col_mime_type,
                    r_att.col_file_size,
                    v_author_id,
                    NOW(),
                    NOW(),
                    0
                );
                v_atts_linked := v_atts_linked + 1;
                RAISE NOTICE '      Linked existing % ("%")', r_att.col_attachment_type, r_att.col_file_name;
            END IF;
        END LOOP;
    END LOOP;

    RAISE NOTICE '=======================================================';
    RAISE NOTICE 'SUMMARY: % books created, % books re-assigned, % attachments linked.', 
                 v_books_created, v_books_updated, v_atts_linked;
    RAISE NOTICE '=======================================================';
END $$;
