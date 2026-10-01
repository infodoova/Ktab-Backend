package com.doova.ktab.features.extraction.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import static com.doova.ktab.features.extraction.ExtractionTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfValidationServiceTest {

    private final PdfValidationService validator = new PdfValidationService(50L * 1024 * 1024);

    @Test
    void rejectsEmptyNonPdfEncryptedAndFullyScannedFiles() {
        assertThatThrownBy(() -> validator.load(new byte[0]))
                .hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.EMPTY);
        assertThatThrownBy(() -> validator.load(fixture("not-a-pdf.pdf")))
                .hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.NOT_PDF);
        assertThatThrownBy(() -> validator.load(fixture("encrypted.pdf")))
                .hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.ENCRYPTED);
        assertThatThrownBy(() -> validator.load(fixture("image-only.pdf")))
                .hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.NO_TEXT_LAYER);
    }

    @Test
    void rejectsACorruptedPdf() {
        byte[] truncated = java.util.Arrays.copyOf(fixture("book-headings.pdf"), 400);
        assertThatThrownBy(() -> validator.load(truncated))
                .hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.CORRUPTED);
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        assertThatThrownBy(() -> new PdfValidationService(10).load(fixture("book-headings.pdf")))
                .hasFieldOrPropertyWithValue("reason", PdfRejectedException.Reason.TOO_LARGE);
    }

    @Test
    void acceptsADigitalArabicPdf() throws Exception {
        try (PDDocument doc = validator.load(fixture("book-headings.pdf"))) {
            assertThat(doc.getNumberOfPages()).isEqualTo(14);
        }
    }

    @Test
    void aHybridBookWithAFewScannedPagesIsStillAccepted() throws Exception {
        try (PDDocument doc = validator.load(fixture("book-hybrid.pdf"))) {
            assertThat(doc.getNumberOfPages()).isEqualTo(14);
        }
    }
}
