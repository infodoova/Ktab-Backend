-- Update default AI model for in-book generated images to gemini-3.1-flash-image
ALTER TABLE tbl_generated_images
    ALTER COLUMN col_ai_model SET DEFAULT 'gemini-3.1-flash-image';
