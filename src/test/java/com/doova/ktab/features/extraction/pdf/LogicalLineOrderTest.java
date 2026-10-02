package com.doova.ktab.features.extraction.pdf;

import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.text.ArabicTextCleaner;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real publisher PDFs (found on a real 800-page Arabic book) store a justified line as separate phrases written in
 * visual left-to-right order. PDFBox keeps that order, so the sentence came out backwards. The extractor must put the
 * phrases of an Arabic line back in right-to-left reading order, using where they sit on the page.
 */
class LogicalLineOrderTest {

    private static final File FONT = new File("src/test/resources/fonts/Cairo.ttf");

    private static String visual(String logical) {
        return new StringBuilder(logical).reverse().toString(); // glyphs stored in visual order, as in the real PDFs
    }

    /** One text line = phrases (given in reading order) laid out right-to-left, but written to the stream left-to-right. */
    private static byte[] justifiedLines(List<List<String>> lines, boolean streamInVisualOrder) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = PDType0Font.load(doc, FONT);
            float size = 14;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(font, size);
                float y = 760;
                for (List<String> phrases : lines) {
                    float[] x = new float[phrases.size()];
                    float cursor = 540; // the right margin: the first phrase of an Arabic line sits at the right
                    for (int i = 0; i < phrases.size(); i++) {
                        float w = font.getStringWidth(phrases.get(i)) / 1000f * size;
                        cursor -= w;
                        x[i] = cursor;
                        cursor -= 38; // justification gap
                    }
                    List<Integer> order = new java.util.ArrayList<>();
                    for (int i = 0; i < phrases.size(); i++) {
                        order.add(i);
                    }
                    if (streamInVisualOrder) {
                        java.util.Collections.reverse(order); // leftmost phrase first, as the publisher PDFs do
                    }
                    for (int i : order) {
                        cs.beginText();
                        cs.newLineAtOffset(x[i], y);
                        cs.showText(visual(phrases.get(i)));
                        cs.endText();
                    }
                    y -= 24;
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static List<String> linesOf(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PageContent page = new PdfPageExtractor().extract(doc).get(0);
            return page.lines().stream().filter(l -> !l.isBlank()).map(String::strip).toList();
        }
    }

    @Test
    void phrasesWrittenInVisualOrderAreReadRightToLeft() throws Exception {
        byte[] pdf = justifiedLines(List.of(
                List.of("وقد أحرز الرجلان", "نجاحا باهرا", "في مساعيهما", "لإعلاء شأن فارس"),
                List.of("حتى بات ذكرهما", "يتردد كثيرا", "في صفحات النميمة")), true);

        assertThat(linesOf(pdf)).containsExactly(
                "وقد أحرز الرجلان نجاحا باهرا في مساعيهما لإعلاء شأن فارس",
                "حتى بات ذكرهما يتردد كثيرا في صفحات النميمة");
    }

    @Test
    void phrasesAlreadyInReadingOrderStayThatWay() throws Exception {
        byte[] pdf = justifiedLines(List.of(List.of("وقد أحرز الرجلان", "نجاحا باهرا", "في مساعيهما")), false);

        assertThat(linesOf(pdf)).containsExactly("وقد أحرز الرجلان نجاحا باهرا في مساعيهما");
    }

    @Test
    void aSingleUnbrokenLineIsUntouched() throws Exception {
        byte[] pdf = justifiedLines(List.of(List.of("وفي المأدبة الكبرى تضمنت قائمة الطعام")), true);

        assertThat(linesOf(pdf)).containsExactly("وفي المأدبة الكبرى تضمنت قائمة الطعام");
    }

    @Test
    void duplicatedDiacriticMarksCollapseToOne() {
        assertThat(ArabicTextCleaner.collapseDuplicateMarks("جوًًا")).isEqualTo("جوًا");
        assertThat(ArabicTextCleaner.collapseDuplicateMarks("مََن")).isEqualTo("مَن");
        assertThat(ArabicTextCleaner.collapseDuplicateMarks("المشويّّ والمحشو")).isEqualTo("المشويّ والمحشو");
        assertThat(ArabicTextCleaner.collapseDuplicateMarks("ضفةِِ نهرٍٍ")).isEqualTo("ضفةِ نهرٍ");
    }

    @Test
    void differentMarksAndRealTextAreLeftAlone() {
        String text = "كَتَبَ الطَّالِبُ الدَّرْسَ في مُحَمَّدٍ";
        assertThat(ArabicTextCleaner.collapseDuplicateMarks(text)).isEqualTo(text);
        assertThat(ArabicTextCleaner.collapseDuplicateMarks(null)).isNull();
    }
}
