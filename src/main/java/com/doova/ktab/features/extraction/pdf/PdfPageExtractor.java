package com.doova.ktab.features.extraction.pdf;

import com.doova.ktab.features.extraction.dto.PageContent;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Spec step 3: every page independently, with its physical 1-based PDF number. Raw text only; cleaning comes next. */
@Component
public class PdfPageExtractor {

    private static final int IMAGE_ONLY_MAX_CHARS = 50;

    public List<PageContent> extract(PDDocument doc) {
        List<PageContent> pages = new ArrayList<>();
        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
            try {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                stripper.setSortByPosition(true);
                stripper.setLineSeparator("\n");
                String raw = stripper.getText(doc).strip();
                List<String> lines = raw.isEmpty() ? List.of() : Arrays.asList(raw.split("\n", -1));
                boolean imageOnly = raw.replaceAll("\\s+", "").length() <= IMAGE_ONLY_MAX_CHARS && hasImage(doc.getPage(i - 1));
                pages.add(new PageContent(i, raw, raw, null, null, lines, imageOnly));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read PDF page " + i, e);
            }
        }
        return pages;
    }

    private static boolean hasImage(PDPage page) throws IOException {
        PDResources resources = page.getResources();
        if (resources == null) {
            return false;
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject x = resources.getXObject(name);
            if (x instanceof PDImageXObject) {
                return true;
            }
        }
        return false;
    }
}
