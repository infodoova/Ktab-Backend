package com.doova.ktab.utils.validator;

import com.doova.ktab.enums.book.PdfType;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.ingestion.config.IngestionProperties;
import com.doova.ktab.features.ingestion.pdf.PdfClassificationResult;
import com.doova.ktab.features.ingestion.pdf.PdfTypeClassifier;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfValidatorTest {

    @Mock
    private PdfTypeClassifier mockClassifier;

    private PdfValidator validatorWithRealClassifier;
    private PdfValidator validatorWithMockClassifier;

    private static final String SAMPLE_TEXT =
            "This is a digital PDF document containing selectable text layers for testing purposes and verification. "
                    + "It has sufficient characters to satisfy the digital text layer requirements of the classifier.";

    @BeforeEach
    void setUp() {
        IngestionProperties properties = new IngestionProperties();
        PdfTypeClassifier realClassifier = new PdfTypeClassifier(properties);
        validatorWithRealClassifier = new PdfValidator(realClassifier);
        validatorWithMockClassifier = new PdfValidator(mockClassifier);
    }

    private byte[] createDigitalPdfBytes(String text) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createScannedPdfBytes() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            BufferedImage bim = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bim.createGraphics();
            g.setColor(Color.LIGHT_GRAY);
            g.fillRect(0, 0, 800, 1000);
            g.dispose();
            PDImageXObject image = LosslessFactory.createFromImage(doc, bim);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(image, 0, 0, page.getCropBox().getWidth(), page.getCropBox().getHeight());
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("validatePdf with digital PDF should succeed")
    void validatePdf_digitalPdfWithSelectableText_success() throws Exception {
        byte[] pdfBytes = createDigitalPdfBytes(SAMPLE_TEXT);
        MockMultipartFile file = new MockMultipartFile(
                "file", "book.pdf", "application/pdf", pdfBytes);

        assertDoesNotThrow(() -> validatorWithRealClassifier.validatePdf(file));
    }

    @Test
    @DisplayName("validatePdf with digital PDF and language specified should succeed")
    void validatePdf_digitalPdfWithLanguageSpecified_success() throws Exception {
        byte[] pdfBytes = createDigitalPdfBytes(SAMPLE_TEXT);
        MockMultipartFile file = new MockMultipartFile(
                "file", "book.pdf", "application/pdf", pdfBytes);

        assertDoesNotThrow(() -> validatorWithRealClassifier.validatePdf(file, "en"));
    }

    @Test
    @DisplayName("validatePdf with scanned image-only PDF should throw PDF_NOT_DIGITAL")
    void validatePdf_scannedImageOnlyPdf_throwsPdfNotDigital() throws Exception {
        byte[] pdfBytes = createScannedPdfBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "scanned.pdf", "application/pdf", pdfBytes);

        assertThatThrownBy(() -> validatorWithRealClassifier.validatePdf(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_NOT_DIGITAL.getKey());
    }

    @Test
    @DisplayName("validatePdf when classifier returns SCANNED should throw PDF_NOT_DIGITAL")
    void validatePdf_whenClassifierReturnsScanned_throwsPdfNotDigital() throws Exception {
        byte[] pdfBytes = createDigitalPdfBytes(SAMPLE_TEXT);
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", pdfBytes);

        when(mockClassifier.classify(any(), any()))
                .thenReturn(PdfClassificationResult.builder().pdfType(PdfType.SCANNED).build());

        assertThatThrownBy(() -> validatorWithMockClassifier.validatePdf(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_NOT_DIGITAL.getKey());
    }

    @Test
    @DisplayName("validatePdf when classifier returns MIXED should throw PDF_NOT_DIGITAL")
    void validatePdf_whenClassifierReturnsMixed_throwsPdfNotDigital() throws Exception {
        byte[] pdfBytes = createDigitalPdfBytes(SAMPLE_TEXT);
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", pdfBytes);

        when(mockClassifier.classify(any(), any()))
                .thenReturn(PdfClassificationResult.builder().pdfType(PdfType.MIXED).build());

        assertThatThrownBy(() -> validatorWithMockClassifier.validatePdf(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_NOT_DIGITAL.getKey());
    }

    @Test
    @DisplayName("validatePdf when classifier returns UNKNOWN should throw PDF_NOT_DIGITAL")
    void validatePdf_whenClassifierReturnsUnknown_throwsPdfNotDigital() throws Exception {
        byte[] pdfBytes = createDigitalPdfBytes(SAMPLE_TEXT);
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", pdfBytes);

        when(mockClassifier.classify(any(), any()))
                .thenReturn(PdfClassificationResult.unknown("unclassifiable", 1));

        assertThatThrownBy(() -> validatorWithMockClassifier.validatePdf(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_NOT_DIGITAL.getKey());
    }

    @Test
    @DisplayName("validatePdf with empty file should throw PDF_EMPTY")
    void validatePdf_emptyFile_throwsPdfEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validatorWithRealClassifier.validatePdf(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_EMPTY.getKey());
    }

    @Test
    @DisplayName("validatePdf with null file should throw PDF_EMPTY")
    void validatePdf_nullFile_throwsPdfEmpty() {
        assertThatThrownBy(() -> validatorWithRealClassifier.validatePdf(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_EMPTY.getKey());
    }

    @Test
    @DisplayName("validatePdf with blank filename should throw PDF_INVALID_FILENAME")
    void validatePdf_blankFilename_throwsPdfInvalidFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "   ", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validatorWithRealClassifier.validatePdf(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_INVALID_FILENAME.getKey());
    }

    @Test
    @DisplayName("validatePdf with non-pdf extension should throw PDF_INVALID_EXTENSION")
    void validatePdf_invalidExtension_throwsPdfInvalidExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.docx", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validatorWithRealClassifier.validatePdf(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_INVALID_EXTENSION.getKey());
    }

    @Test
    @DisplayName("validatePdf with corrupted bytes should throw PDF_CORRUPTED")
    void validatePdf_corruptedBytes_throwsPdfCorrupted() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "corrupted.pdf", "application/pdf", "not a pdf header".getBytes());

        assertThatThrownBy(() -> validatorWithRealClassifier.validatePdf(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_CORRUPTED.getKey());
    }
}
