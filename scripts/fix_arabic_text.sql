-- ============================================================================
-- Fix broken Arabic diacritics / presentation forms in book page text
--
-- Targets the glitches seen in the reader: a tanween or other mark floating on its own ("مراد ً فًا",
-- "الرسال ً ة"), tatweel glued to a mark, presentation-form characters (U+FB50-FDFF, U+FE70-FEFF) that a PDF
-- extraction left behind, repeated marks, and invisible direction/zero-width characters inside words.
--
-- Usage (on the server, from the project folder):
--   docker exec -i ktab-db psql -U ktab_user -d ktab < scripts/fix_arabic_text.sql                  # preview only
--   docker exec -i ktab-db psql -U ktab_user -d ktab -v apply=true < scripts/fix_arabic_text.sql    # really fix
-- The default run only creates the function and PRINTS counts and examples; nothing in the book pages changes.
-- Take a backup first (bash scripts/backup_db.sh). The apply run also keeps the old text of every changed page
-- in tbl_book_pages_arabic_fix_backup.
--
-- What it cannot do: restore letters that the extraction dropped or put in the wrong order
-- (for example a word split into "مراد" + "فًا"). Those need the page re-extracted or a manual edit.
-- ============================================================================

\if :{?apply}
\else
  \set apply false
\endif

CREATE OR REPLACE FUNCTION fn_fix_arabic_text(t TEXT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    r TEXT := t;
BEGIN
    IF r IS NULL OR r = '' THEN
        RETURN r;
    END IF;

    -- 1. Isolated-mark presentation forms (U+FE70-FE7F) -> the plain mark (no stray space is created);
    --    then the remaining presentation forms (letter shapes, ligatures) -> normal letters.
    r := translate(r,
        U&'\FE70\FE71\FE72\FE74\FE76\FE77\FE78\FE79\FE7A\FE7B\FE7C\FE7D\FE7E\FE7F',
        U&'\064B\064B\064C\064D\064E\064E\064F\064F\0650\0650\0651\0651\0652\0652');
    IF r ~ '[ﭐ-﷿ﹰ-﻿]' THEN
        r := normalize(r, NFKC);
    END IF;

    -- 2. Invisible junk: soft hyphen, zero-width space, BOM, bidi embedding/override controls.
    r := regexp_replace(r, '[­​﻿‪-‮]', '', 'g');

    -- 3. Canonical order of marks, so that a repeated mark ends up next to its twin and step 7 catches it.
    r := normalize(r, NFC);

    -- 4. A tatweel (kashida) glued to a mark: the stretch is useless and renders as a floating mark.
    r := regexp_replace(r, 'ـ+([ً-ْٰ])', '\1', 'g');

    -- 5. A mark standing alone between spaces: remove it together with the space before it.
    r := regexp_replace(r, '[ \t]+[ً-ْٰ]+(?=[ \t])', '', 'g');

    -- 6. A mark with no Arabic letter before it (start of text, after a space, digit or punctuation) is an orphan.
    r := regexp_replace(r, '(^|[^ء-ْٰٱ-ۓ])[ً-ْٰ]+', '\1', 'g');

    -- 7. The same mark repeated.
    r := regexp_replace(r, '([ً-ْ])\1+', '\1', 'g');

    -- 8. Two different tanween on one letter: keep the first.
    r := regexp_replace(r, '([ً-ٍ])[ً-ٍ]+', '\1', 'g');

    -- 9. A tanween written before a taa marbuta belongs on the taa marbuta (رسالًة -> رسالةً).
    r := regexp_replace(r, '([ً-ٍ])(ة)', '\2\1', 'g');

    RETURN r;
END $$;

-- ----------------------------------------------------------------------------
-- What would change (no data is touched here)
-- ----------------------------------------------------------------------------
SELECT 'col_markdown_content' AS column_name,
       count(*) FILTER (WHERE col_markdown_content IS DISTINCT FROM fn_fix_arabic_text(col_markdown_content)) AS pages_to_change
FROM tbl_book_pages
UNION ALL
SELECT 'col_markdown_clean',
       count(*) FILTER (WHERE col_markdown_clean IS DISTINCT FROM fn_fix_arabic_text(col_markdown_clean))
FROM tbl_book_pages
UNION ALL
SELECT 'col_footnotes_markdown',
       count(*) FILTER (WHERE col_footnotes_markdown IS DISTINCT FROM fn_fix_arabic_text(col_footnotes_markdown))
FROM tbl_book_pages;

SELECT col_book_id, col_page_number,
       substr(col_markdown_clean, 1, 100)                      AS before,
       substr(fn_fix_arabic_text(col_markdown_clean), 1, 100) AS after
FROM tbl_book_pages
WHERE col_markdown_clean IS DISTINCT FROM fn_fix_arabic_text(col_markdown_clean)
ORDER BY col_book_id, col_page_number
LIMIT 5;

-- ----------------------------------------------------------------------------
-- Apply (only with -v apply=true). One transaction; the old text goes to a backup table first.
-- ----------------------------------------------------------------------------
\if :apply
BEGIN;

CREATE TABLE IF NOT EXISTS tbl_book_pages_arabic_fix_backup (
    col_id                 BIGINT PRIMARY KEY,
    col_markdown_content   TEXT,
    col_markdown_clean     TEXT,
    col_footnotes_markdown TEXT,
    backed_up_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO tbl_book_pages_arabic_fix_backup (col_id, col_markdown_content, col_markdown_clean, col_footnotes_markdown)
SELECT col_id, col_markdown_content, col_markdown_clean, col_footnotes_markdown
FROM tbl_book_pages
WHERE col_markdown_content   IS DISTINCT FROM fn_fix_arabic_text(col_markdown_content)
   OR col_markdown_clean     IS DISTINCT FROM fn_fix_arabic_text(col_markdown_clean)
   OR col_footnotes_markdown IS DISTINCT FROM fn_fix_arabic_text(col_footnotes_markdown)
ON CONFLICT (col_id) DO NOTHING;   -- never overwrite the first (original) backup of a page

UPDATE tbl_book_pages
SET col_markdown_content   = fn_fix_arabic_text(col_markdown_content),
    col_markdown_clean     = fn_fix_arabic_text(col_markdown_clean),
    col_footnotes_markdown = fn_fix_arabic_text(col_footnotes_markdown),
    updated_at             = now()
WHERE col_markdown_content   IS DISTINCT FROM fn_fix_arabic_text(col_markdown_content)
   OR col_markdown_clean     IS DISTINCT FROM fn_fix_arabic_text(col_markdown_clean)
   OR col_footnotes_markdown IS DISTINCT FROM fn_fix_arabic_text(col_footnotes_markdown);

COMMIT;

SELECT count(*) AS pages_fixed_and_backed_up FROM tbl_book_pages_arabic_fix_backup;
-- Undo:
--   UPDATE tbl_book_pages p SET col_markdown_content = b.col_markdown_content,
--          col_markdown_clean = b.col_markdown_clean, col_footnotes_markdown = b.col_footnotes_markdown
--   FROM tbl_book_pages_arabic_fix_backup b WHERE b.col_id = p.col_id;
\endif
