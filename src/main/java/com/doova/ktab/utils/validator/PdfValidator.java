package com.doova.ktab.utils.validator;

import com.doova.ktab.enums.ApiMessageKey;
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

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final int MIN_PAGES = 1;
    private static final int MAX_PAGES = 2000;
    private static final int MIN_TEXT_LENGTH = 50;

    public void validatePdf(MultipartFile file) throws IOException {

        String name = file.getOriginalFilename();
        if (name == null) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_INVALID_FILENAME.getKey());
        }

        if (!name.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_INVALID_EXTENSION.getKey());
        }

        if (!"application/pdf".equals(file.getContentType())) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_INVALID_MIME.getKey());
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_SIZE_EXCEEDED.getKey());
        }

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(file.getInputStream()))) {

            if (document.isEncrypted()) {
                throw new IllegalArgumentException(ApiMessageKey.PDF_ENCRYPTED.getKey());
            }

            int pages = document.getNumberOfPages();

            if (pages < MIN_PAGES) {
                throw new IllegalArgumentException(ApiMessageKey.PDF_EMPTY.getKey());
            }

            if (pages > MAX_PAGES) {
                throw new IllegalArgumentException(ApiMessageKey.PDF_PAGES_EXCEEDED.getKey());
            }

//            PDFTextStripper stripper = new PDFTextStripper();
//            String text = stripper.getText(document);
//
//            if (text == null || text.trim().length() < MIN_TEXT_LENGTH) {
//                throw new IllegalArgumentException(ApiMessageKey.PDF_NO_SELECTABLE_TEXT.getKey());
//            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_CORRUPTED.getKey());
        }
    }
}
