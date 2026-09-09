package com.doova.ktab.utils.validator;

import com.doova.ktab.enums.message.ApiMessageKey;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Component
public class PdfValidator {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int MIN_PAGES = 1;
    private static final int MAX_PAGES = 2000;

    public void validatePdf(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_EMPTY.getKey());
        }

        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_INVALID_FILENAME.getKey());
        }

        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_INVALID_EXTENSION.getKey());
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_SIZE_EXCEEDED.getKey());
        }

        // Do not trust MultipartFile#getContentType as proof of file type.
        // PDFBox parsing below is the authoritative validation step.
        try (InputStream inputStream = file.getInputStream();
             RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(inputStream);
             PDDocument document = Loader.loadPDF(buffer)) {

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
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_ENCRYPTED.getKey(), e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException(ApiMessageKey.PDF_CORRUPTED.getKey(), e);
        }
    }
}
