-- ============================================================================
-- SQL Seed Script: Dar Hashem (دار هاشم) Library Organization Setup
-- Includes:
--   1. Library Organization (Dar Hashem - Beirut, Lebanon)
--   2. Admin Librarian: Ali Hashem (ali@darhashem.com, role: 35)
--   3. Librarian Staff: Zakaria Zakaria (zakariya@darhashem.com, role: 30)
--   4. Books linked to Dar Hashem
--
-- Note: Default password for both users is: Password123!
--       BCrypt Hash: $2a$10$TjIpHdJoqUd3qNfZn8095eGhTXEn3ZU3Nia2iVxM7cqgodt.EWKzy
-- ============================================================================

DO $$
DECLARE
    v_org_id       BIGINT;
    v_admin_id     BIGINT;
    v_librarian_id BIGINT;
    v_main_genre   BIGINT;
    v_sub_genre    BIGINT;
BEGIN
    -- ------------------------------------------------------------------------
    -- 1. Create or retrieve Library Organization: Dar Hashem
    -- ------------------------------------------------------------------------
    INSERT INTO tbl_library_organizations (
        col_name,
        col_slug,
        col_description,
        col_city,
        col_country,
        col_address,
        col_website,
        col_email,
        col_phone,
        col_status,
        created_at,
        updated_at,
        version
    ) VALUES (
        'Dar Hashem',
        'dar-hashem',
        'دار هاشم للكتب والنشر هي دار نشر عربية مقرها بيروت، متخصصة في الكتب السياسية وشؤون الشرق الأوسط والسير والجيوسياسة والقضية الفلسطينية، وتهدف إلى تقديم محتوى معرفي وأبحاث وترجمات نوعية للقارئ العربي.',
        'Beirut',
        'Lebanon',
        'Beirut, Lebanon',
        'https://darhashem.com',
        'books@darhashem.com',
        '+96170611220',
        'ACTIVE',
        NOW(),
        NOW(),
        0
    )
    ON CONFLICT (col_slug) DO UPDATE
        SET col_name = EXCLUDED.col_name,
            col_description = EXCLUDED.col_description,
            col_city = EXCLUDED.col_city,
            col_country = EXCLUDED.col_country,
            col_address = EXCLUDED.col_address,
            col_email = EXCLUDED.col_email,
            col_phone = EXCLUDED.col_phone,
            updated_at = NOW()
    RETURNING col_id INTO v_org_id;

    RAISE NOTICE 'Library Organization Dar Hashem ID: %', v_org_id;

    -- ------------------------------------------------------------------------
    -- 2. Create or retrieve Admin Librarian: Ali Hashem (Role: 35)
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
            col_library_organization_id = v_org_id,
            col_is_active = '1',
            updated_at = NOW()
    RETURNING col_id INTO v_admin_id;

    RAISE NOTICE 'Admin Librarian User ID: %', v_admin_id;

    -- ------------------------------------------------------------------------
    -- 3. Create or retrieve Librarian Staff: Zakaria Zakaria (Role: 30)
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
        'zakariya@darhashem.com',
        'Zakaria',
        'Zakaria',
        '$2a$10$TjIpHdJoqUd3qNfZn8095eGhTXEn3ZU3Nia2iVxM7cqgodt.EWKzy', -- Password123!
        '30',                                                           -- LIBRARIAN
        '1',                                                            -- ACTIVE
        v_org_id,
        NOW(),
        NOW(),
        0
    )
    ON CONFLICT (col_email) DO UPDATE
        SET col_role = '30',
            col_library_organization_id = v_org_id,
            col_is_active = '1',
            updated_at = NOW()
    RETURNING col_id INTO v_librarian_id;

    RAISE NOTICE 'Librarian Staff User ID: %', v_librarian_id;

    -- ------------------------------------------------------------------------
    -- 4. Retrieve fallback Main and Sub Genre IDs
    -- ------------------------------------------------------------------------
    SELECT col_id INTO v_main_genre FROM tbl_main_genres ORDER BY col_id LIMIT 1;
    SELECT col_id INTO v_sub_genre FROM tbl_sub_genres ORDER BY col_id LIMIT 1;

    -- ------------------------------------------------------------------------
    -- 5. Insert Sample Books for Dar Hashem Library
    -- ------------------------------------------------------------------------
    INSERT INTO tbl_books (
        col_title,
        col_description,
        col_custom_author_name,
        col_book_source,
        col_library_organization_id,
        col_uploader_id,
        col_language,
        col_age_range_min,
        col_age_range_max,
        col_page_count,
        col_has_audio,
        col_status,
        col_ocr_status,
        col_publish_date,
        main_genre_id,
        sub_genre_id,
        created_at,
        updated_at,
        version
    ) VALUES (
        'القضية الفلسطينية والصراع العربي الإسرائيلي',
        'دراسة توثيقية تحليلية في أبعاد القضية الفلسطينية والتحولات الجيوسياسية في الشرق الأوسط.',
        'علي هاشم',
        'LIBRARY',
        v_org_id,
        v_librarian_id,
        'ar',
        15,
        99,
        250,
        false,
        'PUBLISHED',
        'COMPLETED',
        NOW(),
        v_main_genre,
        v_sub_genre,
        NOW(),
        NOW(),
        0
    );

    INSERT INTO tbl_books (
        col_title,
        col_description,
        col_custom_author_name,
        col_book_source,
        col_library_organization_id,
        col_uploader_id,
        col_language,
        col_age_range_min,
        col_age_range_max,
        col_page_count,
        col_has_audio,
        col_status,
        col_ocr_status,
        col_publish_date,
        main_genre_id,
        sub_genre_id,
        created_at,
        updated_at,
        version
    ) VALUES (
        'جيوسياسة الشرق الأوسط وموازين القوى',
        'مجموعة أبحاث استراتيجية صادرة عن دار هاشم حول التوازنات الإقليمية والدولية.',
        'دار هاشم للنشر',
        'LIBRARY',
        v_org_id,
        v_librarian_id,
        'ar',
        16,
        99,
        180,
        false,
        'PUBLISHED',
        'COMPLETED',
        NOW(),
        v_main_genre,
        v_sub_genre,
        NOW(),
        NOW(),
        0
    );

    RAISE NOTICE 'Successfully populated Dar Hashem library organization, staff, and books!';
END $$;
