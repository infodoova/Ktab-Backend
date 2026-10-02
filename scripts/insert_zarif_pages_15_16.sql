-- ============================================================================
-- SQL Script: Insert OCR Content for Pages 15 & 16 (Body Text Only)
-- Book: "The Audacity of Resilience: An Insider’s View into the Making of Iranian Foreign Policy"
-- Author: M. Javad Zarif
-- Table: tbl_book_pages
-- ============================================================================

DO $$
DECLARE
    v_book_id   BIGINT;
    v_author_id BIGINT;
BEGIN
    -- 1. Find the book ID by title or author
    SELECT col_id, col_author_id
    INTO v_book_id, v_author_id
    FROM tbl_books
    WHERE col_title ILIKE '%Audacity of Resilience%'
       OR col_title ILIKE '%Zarif%'
    ORDER BY col_id DESC
    LIMIT 1;

    IF v_book_id IS NULL THEN
        RAISE EXCEPTION 'Book "The Audacity of Resilience" was not found in tbl_books. Please ensure the book record exists first.';
    END IF;

    RAISE NOTICE 'Target Book ID found: %', v_book_id;

    -- ------------------------------------------------------------------------
    -- 2. Insert or Update Page 15
    -- ------------------------------------------------------------------------
    INSERT INTO tbl_book_pages (
        col_book_id,
        col_page_number,
        col_markdown_content,
        col_ocr_status,
        col_error_message,
        col_word_count,
        col_created_by,
        col_last_modified_by,
        created_at,
        updated_at,
        version
    ) VALUES (
        v_book_id,
        15,
        'Despite its impact on global peace and security, Iran’s foreign policy—and its decision-making more broadly—is rarely understood inside Iran, let alone around the world. One of the most serious challenges is the mutual cognitive deficit. The world lacks proper recognition of the complexities of Iran, its concerns, and its patterns of decision-making, and conversely Iranian polity has inadequate appreciation of the intricacies of the outside world. Having studied and served in the United States, worked in senior diplomatic positions in Iran, and had my own share of cognitive shortcomings over the past half-century, I have arrived at certain personal reflections. I shared them with my compatriots over two years ago, when my analytical memoirs—rather than a conventional chronological autobiography—entitled The Audacity of Resilience: Reflections on Eight Years as Foreign Minister¹, were published in Persian and warmly received by Iranian readers.',
        'COMPLETED',
        NULL,
        153,
        v_author_id,
        v_author_id,
        NOW(),
        NOW(),
        0
    )
    ON CONFLICT (col_book_id, col_page_number) DO UPDATE
        SET col_markdown_content = EXCLUDED.col_markdown_content,
            col_word_count       = EXCLUDED.col_word_count,
            col_ocr_status       = EXCLUDED.col_ocr_status,
            col_error_message    = NULL,
            col_last_modified_by = EXCLUDED.col_last_modified_by,
            updated_at           = NOW();

    RAISE NOTICE '  [✓] Page 15 inserted/updated for book ID %', v_book_id;

    -- ------------------------------------------------------------------------
    -- 3. Insert or Update Page 16
    -- ------------------------------------------------------------------------
    INSERT INTO tbl_book_pages (
        col_book_id,
        col_page_number,
        col_markdown_content,
        col_ocr_status,
        col_error_message,
        col_word_count,
        col_created_by,
        col_last_modified_by,
        created_at,
        updated_at,
        version
    ) VALUES (
        v_book_id,
        16,
        'Following the translation and publication of the memoirs in Arabic², and the enthusiasm of Arab readers, two accomplished young scholars, Ms. Fatemeh Masoumi and Mr. Kianouche Amiri, translated the entire book from Persian into English. I am deeply indebted to both for their meticulous effort to remain faithful to the original, preserving its integrity, tone, and approach. However, because the book was written for an Iranian audience, it was necessary to make it fully comprehensible to English readers—a task to which I devoted the Summer and Autumn of 2025, with valuable assistance from artificial intelligence³.

Upon rereading the book in English, I concluded that despite the extremely significant developments of the past two years, I should neither change—nor in fact needed to change—any of my analyses, predictions or policy recommendations. Accordingly, I did not alter the substance of the text and only added, at the publisher’s request, two brief sections concerning allegations made against me by my old friend Sergey Lavrov and the purported “snapback” initiated by the United Kingdom, France and Germany⁴. I also added a few sentences—and adjusted several verb tenses from present to past—following the ouster of Bashar al-Assad in Syria, without altering any of the underlying analysis.

Allow me here to make a few observations about the immense transformations in the world, West Asia and Iran since the original Persian edition was published. The tragic helicopter crash that killed Iran’s former president and necessitated a new presidential election drew me into Iranian domestic politics after nearly four decades of public service devoted exclusively to diplomacy. As an active participant in the',
        'COMPLETED',
        NULL,
        280,
        v_author_id,
        v_author_id,
        NOW(),
        NOW(),
        0
    )
    ON CONFLICT (col_book_id, col_page_number) DO UPDATE
        SET col_markdown_content = EXCLUDED.col_markdown_content,
            col_word_count       = EXCLUDED.col_word_count,
            col_ocr_status       = EXCLUDED.col_ocr_status,
            col_error_message    = NULL,
            col_last_modified_by = EXCLUDED.col_last_modified_by,
            updated_at           = NOW();

    RAISE NOTICE '  [✓] Page 16 inserted/updated for book ID %', v_book_id;

END $$;
