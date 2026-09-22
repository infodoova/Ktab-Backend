import os
import sys
import psycopg2

sys.stdout.reconfigure(encoding='utf-8')

conn = psycopg2.connect(
    host='localhost',
    port=5432,
    dbname='ktab',
    user='postgres',
    password='123456'
)
cur = conn.cursor()

# 1. Check if author 11 already has this book
cur.execute("""
    SELECT col_id FROM tbl_books 
    WHERE col_title = 'بعض الصوت' AND col_book_source = 'AUTHOR' AND col_author_id = 11;
""")
row = cur.fetchone()

if row:
    author_book_id = row[0]
    print(f"Author book already exists with ID: {author_book_id}")
else:
    print("Inserting author book for author 11...")
    # Check if ID 122 is available to preserve contiguous numbering
    cur.execute("SELECT col_id FROM tbl_books WHERE col_id = 122;")
    if cur.fetchone() is None:
        cur.execute("""
            INSERT INTO tbl_books (
                col_id, col_title, col_description, col_custom_author_name,
                col_book_source, col_author_id, col_uploader_id, col_library_organization_id,
                col_language, col_age_range_min, col_age_range_max, col_page_count,
                col_has_audio, col_average_rating, col_total_reviews, col_status,
                col_ocr_status, col_publish_date, main_genre_id, sub_genre_id,
                created_at, updated_at, version
            ) VALUES (
                122,
                'بعض الصوت',
                'رواية تركية مترجمة تستكشف الوحدة والذاكرة والحب والعلاقات العائلية والضغوط الاجتماعية والاقتصادية، من خلال شخصيات تتقاطع حيواتهم بين الماضي والحاضر ومحاولات البحث عن معنى وانتماء.',
                'بيلغهان أوتشاك',
                'AUTHOR',
                11,
                11,
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
            ) RETURNING col_id;
        """)
        author_book_id = cur.fetchone()[0]
    else:
        cur.execute("""
            INSERT INTO tbl_books (
                col_title, col_description, col_custom_author_name,
                col_book_source, col_author_id, col_uploader_id, col_library_organization_id,
                col_language, col_age_range_min, col_age_range_max, col_page_count,
                col_has_audio, col_average_rating, col_total_reviews, col_status,
                col_ocr_status, col_publish_date, main_genre_id, sub_genre_id,
                created_at, updated_at, version
            ) VALUES (
                'بعض الصوت',
                'رواية تركية مترجمة تستكشف الوحدة والذاكرة والحب والعلاقات العائلية والضغوط الاجتماعية والاقتصادية، من خلال شخصيات تتقاطع حيواتهم بين الماضي والحاضر ومحاولات البحث عن معنى وانتماء.',
                'بيلغهان أوتشاك',
                'AUTHOR',
                11,
                11,
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
            ) RETURNING col_id;
        """)
        author_book_id = cur.fetchone()[0]
    print(f"Created Author Book with ID: {author_book_id}")

# 2. Attachments for Author Book
# Cover
cur.execute("""
    SELECT col_id FROM tbl_attachments 
    WHERE col_entity_id = %s AND col_entity_type = 'Book' AND col_attachment_type = 'COVER_IMAGE';
""", (author_book_id,))
cover_row = cur.fetchone()
if cover_row:
    cur.execute("""
        UPDATE tbl_attachments 
        SET col_file_name = 'cover.jpg',
            col_storage_path = 'libraries/1/books/cover/61d6ef0b-f618-40e2-af50-0ff040ec3e37.jpg',
            col_mime_type = 'image/jpeg',
            col_file_size = 88501,
            col_user_id = 11,
            updated_at = NOW()
        WHERE col_id = %s;
    """, (cover_row[0],))
    print(f"Updated existing COVER_IMAGE attachment {cover_row[0]}")
else:
    cur.execute("""
        INSERT INTO tbl_attachments (
            col_file_name, col_storage_path, col_user_id, col_entity_id,
            col_entity_type, col_attachment_type, col_mime_type, col_file_size,
            col_created_by, created_at, updated_at, version
        ) VALUES (
            'cover.jpg',
            'libraries/1/books/cover/61d6ef0b-f618-40e2-af50-0ff040ec3e37.jpg',
            11,
            %s,
            'Book',
            'COVER_IMAGE',
            'image/jpeg',
            88501,
            11,
            NOW(),
            NOW(),
            0
        ) RETURNING col_id;
    """, (author_book_id,))
    print(f"Inserted COVER_IMAGE attachment {cur.fetchone()[0]}")

# PDF
cur.execute("""
    SELECT col_id FROM tbl_attachments 
    WHERE col_entity_id = %s AND col_entity_type = 'Book' AND col_attachment_type = 'PDF_SOURCE';
""", (author_book_id,))
pdf_row = cur.fetchone()
if pdf_row:
    cur.execute("""
        UPDATE tbl_attachments 
        SET col_file_name = 'بعض الصوت.pdf',
            col_storage_path = 'libraries/1/books/pdf/8b858af7-78e3-437c-9f1c-26356e4d69ed.pdf',
            col_mime_type = 'application/pdf',
            col_file_size = 1336182,
            col_user_id = 11,
            updated_at = NOW()
        WHERE col_id = %s;
    """, (pdf_row[0],))
    print(f"Updated existing PDF_SOURCE attachment {pdf_row[0]}")
else:
    cur.execute("""
        INSERT INTO tbl_attachments (
            col_file_name, col_storage_path, col_user_id, col_entity_id,
            col_entity_type, col_attachment_type, col_mime_type, col_file_size,
            col_created_by, created_at, updated_at, version
        ) VALUES (
            'بعض الصوت.pdf',
            'libraries/1/books/pdf/8b858af7-78e3-437c-9f1c-26356e4d69ed.pdf',
            11,
            %s,
            'Book',
            'PDF_SOURCE',
            'application/pdf',
            1336182,
            11,
            NOW(),
            NOW(),
            0
        ) RETURNING col_id;
    """, (author_book_id,))
    print(f"Inserted PDF_SOURCE attachment {cur.fetchone()[0]}")

# 3. Synchronize sequences
cur.execute("SELECT setval('tbl_books_col_id_seq', (SELECT MAX(col_id) FROM tbl_books));")
cur.execute("SELECT setval('tbl_attachments_col_id_seq', (SELECT MAX(col_id) FROM tbl_attachments));")

conn.commit()

# 4. Verify everything for author 11
print("\n--- Current Books for Author 11 ---")
cur.execute("""
    SELECT b.col_id, b.col_title, b.col_book_source, b.col_status,
           COUNT(a.col_id) as att_count
    FROM tbl_books b
    LEFT JOIN tbl_attachments a ON a.col_entity_id = b.col_id AND a.col_entity_type = 'Book'
    WHERE b.col_author_id = 11
    GROUP BY b.col_id, b.col_title, b.col_book_source, b.col_status
    ORDER BY b.col_id;
""")
for r in cur.fetchall():
    print(f"  ID {r[0]}: '{r[1]}' (source: {r[2]}, status: {r[3]}, attachments: {r[4]})")

conn.close()
print("\nCompleted successfully!")
