package com.doova.ktab.features.extraction.fixtures;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Regenerates the committed PDFs in {@code src/test/resources/extraction/}. Chromium shapes the Arabic and embeds a
 * real text layer; PDFBox adds the outline, encryption and image-only pages. Run only when the fixtures must change:
 * <pre>GENERATE_EXTRACTION_FIXTURES=true ./mvnw -q test -Dtest=ExtractionFixtureGenerator</pre>
 *
 * <p>All books share one body: 14 PDF pages. Pages 1-3 are front matter (cover, copyright, dedication or فهرس).
 * Body pages 4-14 carry the running header "اسم الكتاب" and an Arabic-Indic footer number (printed page = PDF page - 3).
 * المقدمة p4, الفصل الأول p6 (المبحث الأول p7, المبحث الثاني p8), الفصل الثاني p10, الخاتمة p13.
 * Page 11 mentions "الفصل الأول" inside a long paragraph (must not be detected as a heading).
 */
@EnabledIfEnvironmentVariable(named = "GENERATE_EXTRACTION_FIXTURES", matches = "true")
class ExtractionFixtureGenerator {

    private static final Path OUT = Path.of("src/test/resources/extraction");
    private static final Path FONT = Path.of("src/main/resources/storybook/fonts/NotoNaskhArabic-Regular.ttf");

    private static final String[] SENTENCES = {
            "يتناول هذا الكتاب قصة مدينة قديمة عاش فيها أهلها بسلام لسنوات طويلة قبل أن تتغير أحوالهم.",
            "كان الشيخ يجلس عند باب داره كل مساء ويحدث الصغار عن أيام الحصاد وعن حكمة الأجداد.",
            "ومن هنا بدأت الحكاية التي ستغير مجرى الأحداث في القرية البعيدة عن صخب المدن الكبرى.",
            "لم يكن أحد يتوقع أن يصل الغريب في ذلك اليوم الماطر حاملا معه أخبارا عن الأراضي المجاورة.",
            "وتتبع الفصول التالية خيوط هذه الرواية بدقة وتفصل ما أجمله الراوي في البداية.",
            "ثم استمرت الأيام على هذا النحو حتى جاء الموسم الذي لم ينسه أحد من سكان الوادي.",
            "وفي ذلك الحين أدرك الجميع أن ما يجمعهم أعمق من كل ما كان يفرقهم عبر الأجيال."
    };

    private static String para(int seed) {
        StringBuilder b = new StringBuilder("<p>");
        for (int i = 0; i < 4; i++) {
            b.append(SENTENCES[(seed + i * 3) % SENTENCES.length]).append(' ');
        }
        return b.append("</p>").toString();
    }

    private static String arabicIndic(int n) {
        StringBuilder b = new StringBuilder();
        for (char c : Integer.toString(n).toCharArray()) {
            b.append((char) ('٠' + (c - '0')));
        }
        return b.toString();
    }

    private static String page(int pdfPage, String body) {
        boolean bodyPage = pdfPage >= 4;
        return "<div class=\"page\">" + (bodyPage ? "<p class=\"rh\">اسم الكتاب</p>" : "") + body
                + (bodyPage ? "<p class=\"pn\">" + arabicIndic(pdfPage - 3) + "</p>" : "") + "</div>";
    }

    private static String bookHtml(String page3) {
        String[] bodies = new String[15];
        bodies[1] = "<h1>كتاب تجريبي</h1><p class=\"c\">مؤلف تجريبي</p>";
        bodies[2] = "<p>جميع الحقوق محفوظة للناشر. لا يجوز نسخ هذا الكتاب أو تخزينه بأي وسيلة دون إذن كتابي.</p>";
        bodies[3] = page3;
        bodies[4] = "<h2>المقدمة</h2>" + para(0) + para(1);
        bodies[5] = para(2) + para(3);
        bodies[6] = "<h2>الفصل الأول</h2>" + para(4) + para(5);
        bodies[7] = "<h3>المبحث الأول</h3>" + para(6) + para(0);
        bodies[8] = "<h3>المبحث الثاني</h3>" + para(1) + para(2);
        bodies[9] = para(3) + para(4);
        bodies[10] = "<h2>الفصل الثاني</h2>" + para(5) + para(6);
        bodies[11] = "<p>" + SENTENCES[0] + " كما ذكرنا في الفصل الأول من هذا الكتاب فإن الأحداث تتوالى. " + SENTENCES[1]
                + " " + SENTENCES[2] + "</p>" + para(3);
        bodies[12] = para(4) + para(5);
        bodies[13] = "<h2>الخاتمة</h2>" + para(6) + para(0);
        bodies[14] = para(1) + para(2);
        StringBuilder pages = new StringBuilder();
        for (int i = 1; i <= 14; i++) {
            pages.append(page(i, bodies[i]));
        }
        return "<!DOCTYPE html><html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\"><style>"
                + "@font-face{font-family:'N';src:url('font.ttf');}"
                + "@page{size:14.8cm 21cm;margin:0}"
                + "html,body{margin:0;padding:0;font-family:'N',serif;font-size:15px;line-height:1.9}"
                + ".page{box-sizing:border-box;width:14.8cm;height:21cm;padding:1.8cm 1.6cm;position:relative;"
                + "page-break-after:always;overflow:hidden}"
                + ".rh{position:absolute;top:0.7cm;left:0;right:0;text-align:center;font-size:12px;margin:0}"
                + ".pn{position:absolute;bottom:0.7cm;left:0;right:0;text-align:center;font-size:12px;margin:0}"
                + "h1{font-size:34px;text-align:center;margin-top:6cm}.c{text-align:center}"
                + "h2{font-size:28px;margin:0 0 14px}h3{font-size:22px;margin:0 0 12px}"
                + ".toc p{margin:6px 0;display:flex}.toc .d{flex:1;overflow:hidden;white-space:nowrap;margin:0 6px;letter-spacing:2px}"
                + "</style></head><body>" + pages + "</body></html>";
    }

    private static String tocPage() {
        String[][] rows = {{"المقدمة", "1", "0"}, {"الفصل الأول", "3", "0"}, {"المبحث الأول", "4", "1"},
                {"المبحث الثاني", "5", "1"}, {"الفصل الثاني", "7", "0"}, {"الخاتمة", "10", "0"}};
        StringBuilder b = new StringBuilder("<h2>فهرس المحتويات</h2><div class=\"toc\">");
        for (String[] r : rows) {
            b.append("<p style=\"padding-right:").append(Integer.parseInt(r[2]) * 28).append("px\"><span>").append(r[0])
                    .append("</span><span class=\"d\">").append(".".repeat(40)).append("</span><span>").append(arabicIndic(Integer.parseInt(r[1]))).append("</span></p>");
        }
        return b.append("</div>").toString();
    }

    private static byte[] render(String html) throws Exception {
        Path dir = Files.createTempDirectory("fixture-");
        Files.copy(FONT, dir.resolve("font.ttf"));
        Path file = dir.resolve("book.html");
        Files.writeString(file, html);
        try (Playwright pw = Playwright.create();
             Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true)
                     .setChromiumSandbox(false).setArgs(List.of("--disable-dev-shm-usage")))) {
            Page page = browser.newPage();
            page.navigate(file.toUri().toString());
            page.waitForLoadState();
            return page.pdf(new Page.PdfOptions().setWidth("14.8cm").setHeight("21cm").setPreferCSSPageSize(true));
        }
    }

    private static byte[] withMetadata(byte[] pdf, boolean outline) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("كتاب تجريبي");
            info.setAuthor("مؤلف تجريبي");
            info.setSubject("اختبار الاستخراج");
            doc.setDocumentInformation(info);
            doc.getDocumentCatalog().setLanguage("ar");
            if (outline) {
                PDDocumentOutline o = new PDDocumentOutline();
                doc.getDocumentCatalog().setDocumentOutline(o);
                PDOutlineItem intro = item(doc, "المقدمة", 4);
                PDOutlineItem ch1 = item(doc, "الفصل الأول", 6);
                ch1.addLast(item(doc, "المبحث الأول", 7));
                ch1.addLast(item(doc, "المبحث الثاني", 8));
                o.addLast(intro);
                o.addLast(ch1);
                o.addLast(item(doc, "الفصل الثاني", 10));
                o.addLast(item(doc, "الخاتمة", 13));
                o.openNode();
                ch1.openNode();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static PDOutlineItem item(PDDocument doc, String title, int pdfPage) {
        PDPageFitDestination dest = new PDPageFitDestination();
        dest.setPage(doc.getPage(pdfPage - 1));
        PDOutlineItem it = new PDOutlineItem();
        it.setTitle(title);
        it.setDestination(dest);
        return it;
    }

    private static PDPage imagePage(PDDocument doc) throws Exception {
        BufferedImage img = new BufferedImage(600, 850, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 850);
        g.setColor(Color.DARK_GRAY);
        for (int y = 80; y < 780; y += 40) {
            g.fillRect(60, y, 480, 10);
        }
        g.dispose();
        PDPage page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(419, 595));
        doc.addPage(page);
        PDImageXObject x = LosslessFactory.createFromImage(doc, img);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(x, 0, 0, 419, 595);
        }
        return page;
    }

    @Test
    void generate() throws Exception {
        Files.createDirectories(OUT);
        byte[] headings = withMetadata(render(bookHtml("<p>إهداء إلى كل من علمني حرفا.</p>")), false);
        byte[] withToc = withMetadata(render(bookHtml(tocPage())), false);
        byte[] outline = withMetadata(render(bookHtml("<p>إهداء إلى كل من علمني حرفا.</p>")), true);
        Files.write(OUT.resolve("book-headings.pdf"), headings);
        Files.write(OUT.resolve("book-printed-toc.pdf"), withToc);
        Files.write(OUT.resolve("book-outline.pdf"), outline);

        try (PDDocument doc = Loader.loadPDF(headings); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pw", "user-pw", ap);
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(out);
            Files.write(OUT.resolve("encrypted.pdf"), out.toByteArray());
        }

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < 3; i++) {
                imagePage(doc);
            }
            doc.save(out);
            Files.write(OUT.resolve("image-only.pdf"), out.toByteArray());
        }

        try (PDDocument doc = Loader.loadPDF(headings); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int pdfPage : new int[]{11, 7}) { // replace page 11 first so index 7 is unaffected
                int idx = pdfPage - 1;
                PDPage replacement = imagePage(doc);   // appended at the end
                doc.getPages().remove(replacement);
                doc.getPages().insertBefore(replacement, doc.getPage(idx));
                doc.removePage(idx + 1);               // drop the original (now one position later)
            }
            doc.save(out);
            Files.write(OUT.resolve("book-hybrid.pdf"), out.toByteArray());
        }

        Files.writeString(OUT.resolve("not-a-pdf.pdf"), "hello");
    }
}
