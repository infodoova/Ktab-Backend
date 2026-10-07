-- A smaller JPEG copy of each page image, served to readers; the full-size PNG stays for QA, the PDF and regeneration.
-- NULL for images made before this column existed: readers fall back to the original.
ALTER TABLE tbl_storybook_page_images ADD COLUMN IF NOT EXISTS col_web_image_key TEXT;
