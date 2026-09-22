package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;
import com.doova.ktab.features.ocr.text.PageLabelParser;
import com.doova.ktab.features.ocr.text.SectionClassifier;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.service.file.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class TextLayerTocSource implements TocSource {

    private final S3OcrStorageService s3;
    private final AttachmentService attachmentService;
    private final BookPageRepository pageRepository;

    private static final Pattern TOC_LINE_PATTERN = Pattern.compile("^(.*?)(?:[\\.\\s\\-_]+)(\\d+|[٠-٩]+)$");

    @Override
    public StructureSource source() {
        return StructureSource.TEXT_LAYER;
    }

    @Override
    public Optional<RawToc> extract(Long bookId) {
        try {
            // Find pages detected as TOC during OCR
            List<BookPage> tocPages = pageRepository.findByBookIdAndPageKindOrderByPageNumberAsc(bookId, PageKind.TOC);
            if (tocPages.isEmpty()) {
                return Optional.empty();
            }

            Optional<Attachment> attachment = attachmentService.getAttachment(bookId, "Book", "PDF_SOURCE");
            if (attachment.isEmpty()) {
                return Optional.empty();
            }

            String pdfKey = attachment.get().getStoragePath();
            try (InputStream in = s3.getStream(pdfKey);
                 RandomAccessRead rar = new RandomAccessReadBuffer(in);
                 PDDocument doc = Loader.loadPDF(rar)) {

                PDFTextStripper stripper = new PDFTextStripper();
                List<RawToc.RawTocEntry> entries = new ArrayList<>();

                for (BookPage tocPage : tocPages) {
                    int pdfPage = tocPage.getSourcePdfPage() != null ? tocPage.getSourcePdfPage() : tocPage.getPageNumber();
                    if (pdfPage > doc.getNumberOfPages()) continue;

                    stripper.setStartPage(pdfPage);
                    stripper.setEndPage(pdfPage);
                    String pageText = stripper.getText(doc);

                    if (!passesArabicSanityCheck(pageText)) {
                        log.debug("PDF text layer failed Arabic sanity check on page {}", pdfPage);
                        return Optional.empty();
                    }

                    parseTocLines(pageText, entries);
                }

                if (entries.size() >= 2) {
                    log.info("Extracted {} TOC entries from text layer for bookId={}", entries.size(), bookId);
                    return Optional.of(new RawToc(entries));
                }
            }
        } catch (Exception e) {
            log.warn("Text layer TOC extraction failed for bookId={}: {}", bookId, e.getMessage());
        }
        return Optional.empty();
    }

    private boolean passesArabicSanityCheck(String text) {
        if (text == null || text.isBlank()) return false;
        int arabicChars = 0;
        int alphaChars = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                alphaChars++;
                // Arabic unicode block U+0600 - U+06FF
                if (c >= '\u0600' && c <= '\u06FF') {
                    arabicChars++;
                }
            }
        }

        return alphaChars > 30 && ((double) arabicChars / alphaChars) > 0.60;
    }

    private void parseTocLines(String text, List<RawToc.RawTocEntry> entries) {
        String[] lines = text.split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) continue;

            Matcher m = TOC_LINE_PATTERN.matcher(line);
            if (m.find()) {
                String title = m.group(1).trim();
                String pageLabel = m.group(2).trim();

                if (!title.isBlank() && !pageLabel.isBlank()) {
                    entries.add(new RawToc.RawTocEntry(
                            title,
                            SectionClassifier.extractDivisionLabel(title).orElse(null),
                            SectionClassifier.extractOrdinal(title).orElse(null),
                            1,
                            ArabicTextNormalizer.convertDigitsToAscii(pageLabel),
                            SectionClassifier.classify(title)
                    ));
                }
            }
        }
    }
}
