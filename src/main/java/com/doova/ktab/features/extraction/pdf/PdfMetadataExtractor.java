package com.doova.ktab.features.extraction.pdf;

import com.doova.ktab.features.extraction.dto.BookMetadata;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.springframework.stereotype.Component;

/** Spec step 2: read what the PDF declares. Blank values become null. */
@Component
public class PdfMetadataExtractor {

    public BookMetadata extract(PDDocument doc) {
        PDDocumentInformation info = doc.getDocumentInformation();
        return new BookMetadata(
                blankToNull(info == null ? null : info.getTitle()),
                blankToNull(info == null ? null : info.getAuthor()),
                blankToNull(info == null ? null : info.getSubject()),
                blankToNull(info == null ? null : info.getKeywords()),
                blankToNull(doc.getDocumentCatalog().getLanguage()),
                doc.getNumberOfPages());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
