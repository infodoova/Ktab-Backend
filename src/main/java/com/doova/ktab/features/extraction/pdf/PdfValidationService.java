package com.doova.ktab.features.extraction.pdf;

import com.doova.ktab.util.text.TextLayerQualityAssessor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Spec step 1: reject what cannot be extracted, and hand back an open document otherwise (the caller closes it). */
@Component
public class PdfValidationService {

    private static final int SAMPLED_PAGES = 20;
    private static final int MIN_CHARS_PER_PAGE = 50;
    private static final double MIN_USABLE_PAGE_RATIO = 0.30;
    private static final double MIN_ARABIC_SCRIPT_RATIO = 0.30;

    private final long maxBytes;

    public PdfValidationService(@Value("${ktab.extraction.max-upload-bytes:209715200}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public PDDocument load(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new PdfRejectedException(PdfRejectedException.Reason.EMPTY, "The file is empty.");
        }
        if (pdf.length > maxBytes) {
            throw new PdfRejectedException(PdfRejectedException.Reason.TOO_LARGE,
                    "The file is larger than the allowed " + maxBytes + " bytes.");
        }
        String head = new String(pdf, 0, Math.min(pdf.length, 1024), StandardCharsets.ISO_8859_1);
        if (!head.contains("%PDF-")) {
            throw new PdfRejectedException(PdfRejectedException.Reason.NOT_PDF, "The file is not a PDF.");
        }
        PDDocument doc;
        try {
            doc = Loader.loadPDF(pdf);
        } catch (InvalidPasswordException e) {
            throw new PdfRejectedException(PdfRejectedException.Reason.ENCRYPTED, "The PDF is password-protected.");
        } catch (IOException e) {
            throw new PdfRejectedException(PdfRejectedException.Reason.CORRUPTED, "The PDF is corrupted: " + e.getMessage());
        }
        try {
            if (doc.isEncrypted()) {
                throw new PdfRejectedException(PdfRejectedException.Reason.ENCRYPTED, "The PDF is encrypted.");
            }
            if (doc.getNumberOfPages() == 0) {
                throw new PdfRejectedException(PdfRejectedException.Reason.CORRUPTED, "The PDF has no pages.");
            }
            requireTextLayer(doc);
            return doc;
        } catch (RuntimeException e) {
            closeQuietly(doc);
            throw e;
        }
    }

    private void requireTextLayer(PDDocument doc) {
        int pages = doc.getNumberOfPages();
        int sampled = Math.min(pages, SAMPLED_PAGES);
        int usable = 0;
        try {
            for (int i = 0; i < sampled; i++) {
                int page = sampled == pages ? i + 1 : 1 + (int) Math.floor((double) i * pages / sampled);
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc);
                String compact = text.replaceAll("\\s+", "");
                if (compact.length() > MIN_CHARS_PER_PAGE
                        && TextLayerQualityAssessor.assess(text, "ar", MIN_ARABIC_SCRIPT_RATIO).passesSanityCheck()) {
                    usable++;
                }
            }
        } catch (IOException e) {
            throw new PdfRejectedException(PdfRejectedException.Reason.CORRUPTED, "The PDF text could not be read: " + e.getMessage());
        }
        if ((double) usable / sampled < MIN_USABLE_PAGE_RATIO) {
            throw new PdfRejectedException(PdfRejectedException.Reason.NO_TEXT_LAYER,
                    "This PDF has no text layer; scanned books are not supported while OCR is off.");
        }
    }

    private static void closeQuietly(PDDocument doc) {
        try {
            doc.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }
}
