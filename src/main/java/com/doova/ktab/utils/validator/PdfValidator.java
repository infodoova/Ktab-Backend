package com.doova.ktab.utils.validator;

import com.doova.ktab.enums.book.PdfType;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.ingestion.config.IngestionProperties;
import com.doova.ktab.features.ingestion.pdf.PdfClassificationResult;
import com.doova.ktab.features.ingestion.pdf.PdfTypeClassifier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final PdfTypeClassifier pdfTypeClassifier;

    public PdfValidator() {
        this(new PdfTypeClassifier(new IngestionProperties()));
    }

    @Autowired
    public PdfValidator(PdfTypeClassifier pdfTypeClassifier) {
        this.pdfTypeClassifier = pdfTypeClassifier != null
                ? pdfTypeClassifier
                : new PdfTypeClassifier(new IngestionProperties());
    }

    public void validatePdf(MultipartFile file) throws IOException {
        validatePdf(file, null);
    }

    public void validatePdf(MultipartFile file, String languageCode) throws IOException {
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

        // Authoritative digital PDF check: must contain selectable text layer (scanned/image-only rejected)
        if (pdfTypeClassifier != null) {
            try (InputStream is = file.getInputStream()) {
                PdfClassificationResult result = pdfTypeClassifier.classify(is, languageCode);
                if (result == null || result.getPdfType() != PdfType.DIGITAL) {
                    throw new IllegalArgumentException(ApiMessageKey.PDF_NOT_DIGITAL.getKey());
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException(ApiMessageKey.PDF_NOT_DIGITAL.getKey(), e);
            }
        }
    }
}
