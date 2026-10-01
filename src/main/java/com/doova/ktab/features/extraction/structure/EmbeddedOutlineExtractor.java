package com.doova.ktab.features.extraction.structure;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Spec step 6: real bookmarks, the preferred structure source. An outline without at least 2 usable entries is ignored. */
@Component
public class EmbeddedOutlineExtractor {

    public List<RawEntry> extract(PDDocument doc) {
        PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();
        if (outline == null) {
            return List.of();
        }
        List<RawEntry> entries = new ArrayList<>();
        walk(doc, outline, 1, entries);
        boolean useless = entries.size() < 2 || entries.stream().allMatch(e -> e.startPage() == 1);
        return useless ? List.of() : entries;
    }

    private void walk(PDDocument doc, PDOutlineNode node, int level, List<RawEntry> out) {
        for (PDOutlineItem item : node.children()) {
            String title = item.getTitle() == null ? "" : item.getTitle().strip();
            Integer page = pageOf(doc, item);
            if (!title.isEmpty() && page != null && page >= 1 && page <= doc.getNumberOfPages()) {
                out.add(new RawEntry(title, level, page, null));
            }
            walk(doc, item, level + 1, out);
        }
    }

    private Integer pageOf(PDDocument doc, PDOutlineItem item) {
        try {
            PDDestination dest = item.getDestination();
            if (dest == null) {
                PDAction action = item.getAction();
                if (action instanceof PDActionGoTo goTo) {
                    dest = goTo.getDestination();
                }
            }
            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            if (dest instanceof PDNamedDestination named) {
                dest = catalog.findNamedDestinationPage(named);
            }
            if (dest instanceof PDPageDestination pageDest) {
                int n = pageDest.retrievePageNumber();
                if (n >= 0) {
                    return n + 1;
                }
                if (pageDest.getPage() != null) {
                    return doc.getPages().indexOf(pageDest.getPage()) + 1;
                }
            }
        } catch (IOException ignored) {
            // an unreadable destination is the same as no destination
        }
        return null;
    }
}
