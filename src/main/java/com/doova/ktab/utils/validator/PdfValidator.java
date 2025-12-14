package com.doova.ktab.utils.validator;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
@Slf4j
public class PdfValidator {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MIN_PAGES = 1;
    private static final int MAX_PAGES = 2000;
    private static final int MIN_SELECTABLE_TEXT_LENGTH = 50; // Minimum text length to qualify as non-scanned

    /**
     * Validates a PDF file with extension, MIME type, size, page count,
     * encryption, and selectable text layer.
     */
    public void validatePdf(MultipartFile file) throws IOException {

        // 1️⃣ Basic validation (extension, MIME, size)
        String name = file.getOriginalFilename();
        if (name == null) {
            throw new IllegalArgumentException("ملف PDF غير صالح (اسم الملف مفقود).");
        }

        String lowerName = name.toLowerCase();

        // Extension
        if (!lowerName.endsWith(".pdf")) {
            throw new IllegalArgumentException("يجب رفع ملف بصيغة PDF فقط.");
        }

        // MIME type
        String mime = file.getContentType();
        if (mime == null || !mime.equals("application/pdf")) {
            throw new IllegalArgumentException("نوع الملف غير صالح. يجب أن يكون PDF.");
        }

        // Size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("حجم ملف الـ PDF يجب ألا يتجاوز 50MB.");
        }

        // 2️⃣ Advanced validation (PDFBox loading)
        try (PDDocument document =
                     Loader.loadPDF(new RandomAccessReadBuffer(file.getInputStream()))) {

            // Encrypted
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("لا يمكن رفع ملفات PDF المحمية بكلمة سر.");
            }

            // Page count
            int pages = document.getNumberOfPages();

            if (pages < MIN_PAGES) {
                throw new IllegalArgumentException("ملف PDF فارغ.");
            }

            if (pages > MAX_PAGES) {
                throw new IllegalArgumentException(
                        "عدد صفحات ملف PDF يتجاوز الحد المسموح (" + MAX_PAGES + ")."
                );
            }

            // Selectable text (reject scanned PDFs)
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            boolean hasSelectableText =
                    text != null &&
                            text.trim().length() > MIN_SELECTABLE_TEXT_LENGTH;

            if (!hasSelectableText) {
                throw new IllegalArgumentException(
                        "الملف يبدو ممسوحًا ضوئيًا ولا يحتوي على نص قابل للنسخ."
                );
            }

        } catch (IllegalArgumentException e) {
            // Re-throw our own validation errors
            throw e;

        } catch (Exception e) {
            // Catch PDFBox errors → corrupted / non-standard PDFs
            throw new IllegalArgumentException("ملف PDF تالف أو غير صالح للمعالجة.");
        }
    }
}
