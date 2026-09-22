package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.features.ocr.text.SectionClassifier;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.service.file.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PdfOutlineTocSource implements TocSource {

    private final S3OcrStorageService s3;
    private final AttachmentService attachmentService;

    @Override
    public StructureSource source() {
        return StructureSource.PDF_OUTLINE;
    }

    @Override
    public Optional<RawToc> extract(Long bookId) {
        try {
            Optional<Attachment> attachment = attachmentService.getAttachment(bookId, "Book", "PDF_SOURCE");
            if (attachment.isEmpty()) {
                return Optional.empty();
            }

            String pdfKey = attachment.get().getStoragePath();
            try (InputStream in = s3.getStream(pdfKey);
                 RandomAccessRead rar = new RandomAccessReadBuffer(in);
                 PDDocument doc = Loader.loadPDF(rar)) {

                PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();
                if (outline == null) {
                    log.debug("No PDF document outline found for bookId={}", bookId);
                    return Optional.empty();
                }

                List<RawToc.RawTocEntry> entries = new ArrayList<>();
                walkOutline(doc, outline, 1, entries);

                if (entries.isEmpty()) {
                    return Optional.empty();
                }

                log.info("Extracted {} TOC entries from PDF outline for bookId={}", entries.size(), bookId);
                return Optional.of(new RawToc(entries));
            }
        } catch (Exception e) {
            log.warn("Failed to extract outline TOC for bookId={}: {}", bookId, e.getMessage());
            return Optional.empty();
        }
    }

    private void walkOutline(PDDocument doc, PDOutlineNode node, int level, List<RawToc.RawTocEntry> entries) {
        for (PDOutlineItem item : node.children()) {
            String title = item.getTitle();
            if (title != null && !title.isBlank()) {
                Integer targetPdfPage = resolveDestinationPage(doc, item);
                String printedLabel = targetPdfPage != null ? String.valueOf(targetPdfPage) : null;

                entries.add(new RawToc.RawTocEntry(
                        title.trim(),
                        SectionClassifier.extractDivisionLabel(title).orElse(null),
                        SectionClassifier.extractOrdinal(title).orElse(null),
                        level,
                        printedLabel,
                        SectionClassifier.classify(title),
                        targetPdfPage
                ));
            }

            // Recurse into child items
            walkOutline(doc, item, level + 1, entries);
        }
    }

    private Integer resolveDestinationPage(PDDocument doc, PDOutlineItem item) {
        try {
            PDDestination dest = item.getDestination();
            if (dest == null && item.getAction() instanceof PDActionGoTo goTo) {
                dest = goTo.getDestination();
            }

            if (dest instanceof PDPageDestination pageDest) {
                int pageNum = pageDest.retrievePageNumber();
                if (pageNum >= 0) {
                    return pageNum + 1; // 1-based PDF page index
                }
            }
        } catch (Exception e) {
            log.trace("Could not resolve destination page for outline item: {}", e.getMessage());
        }
        return null;
    }
}
