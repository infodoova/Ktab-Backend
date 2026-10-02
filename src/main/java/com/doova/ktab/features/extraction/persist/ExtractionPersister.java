package com.doova.ktab.features.extraction.persist;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.book.StructureStatus;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.extraction.dto.BookExtractionResult;
import com.doova.ktab.features.extraction.dto.DetectionSource;
import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.dto.Severity;
import com.doova.ktab.features.extraction.dto.TocEntry;
import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;
import com.doova.ktab.features.ocr.text.SectionClassifier;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes an extraction result into the tables every ingestion pipeline shares (pages, sections, book fields), so the
 * reader, search, talk-to-book and the audiobook jobs work unchanged whichever pipeline produced the book.
 */
@Component
@RequiredArgsConstructor
public class ExtractionPersister {

    private static final double REVIEW_BELOW_CONFIDENCE = 0.8;

    private final BookPageRepository pageRepository;
    private final BookSectionRepository sectionRepository;
    private final BookRepository bookRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void persist(Book book, BookExtractionResult result) {
        if (book.getId() != null) {
            pageRepository.deleteByBook_Id(book.getId());
            sectionRepository.deleteByBook_Id(book.getId());
        }
        DetectionSource detected = result.structureDetection().source();
        boolean noStructure = result.toc().isEmpty();
        double confidence = result.structureDetection().confidence();
        StructureSource source = noStructure ? StructureSource.TEXT_LAYER : toSource(detected);

        List<BookSection> saved = new ArrayList<>();
        int[] order = {0};
        if (noStructure) {
            String title = book.getTitle() == null || book.getTitle().isBlank() ? "الكتاب" : book.getTitle();
            saved.add(saveSection(book, null, title, 1, 1, result.pages().size(), SectionType.CHAPTER, source,
                    BigDecimal.ZERO, true, order));
        } else {
            for (TocEntry e : result.toc()) {
                saveTree(book, null, e, source, confidence, saved, order);
            }
        }

        List<BookPage> pages = new ArrayList<>();
        for (PageContent p : result.pages()) {
            pages.add(toPage(book, p, sectionFor(saved, p.pdfPage())));
        }
        pageRepository.saveAll(pages);

        boolean hasError = result.warnings().stream().anyMatch(w -> w.severity() == Severity.ERROR);
        book.setPageCount(result.metadata().pageCount());
        book.setStructureSource(source);
        book.setStructureStatus(noStructure || hasError ? StructureStatus.NEEDS_REVIEW : StructureStatus.RESOLVED);
        book.setOcrStatus(OcrStatus.COMPLETED);
        book.setTocRaw(toJson(result));
        bookRepository.save(book);
    }

    private void saveTree(Book book, BookSection parent, TocEntry e, StructureSource source, double confidence,
                          List<BookSection> saved, int[] order) {
        boolean review = detectedLowConfidence(source, confidence);
        BookSection s = saveSection(book, parent, e.title(), e.level(), e.startPage(), e.endPage(), sectionType(e),
                source, BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP), review, order);
        saved.add(s);
        for (TocEntry child : e.children()) {
            saveTree(book, s, child, source, confidence, saved, order);
        }
    }

    private static boolean detectedLowConfidence(StructureSource source, double confidence) {
        return source != StructureSource.PDF_OUTLINE && confidence < REVIEW_BELOW_CONFIDENCE;
    }

    private BookSection saveSection(Book book, BookSection parent, String title, int level, int start, int end,
                                    SectionType type, StructureSource source, BigDecimal confidence, boolean review, int[] order) {
        BookSection s = new BookSection();
        s.setBook(book);
        s.setParent(parent);
        s.setTitle(title);
        s.setTitleNormalized(ArabicTextNormalizer.normalize(title));
        s.setLevel((short) level);
        s.setSortOrder(order[0]++);
        s.setStartPage(start);
        s.setEndPage(end);
        s.setSectionType(type);
        s.setSource(source);
        s.setConfidence(confidence);
        s.setNeedsReview(review);
        return sectionRepository.save(s);
    }

    private static SectionType sectionType(TocEntry e) {
        if (e.level() > 1) {
            return SectionType.SUBSECTION;
        }
        SectionType classified = SectionClassifier.classify(e.title());
        return classified == SectionType.OTHER ? SectionType.CHAPTER : classified;
    }

    /** The deepest section that contains the page; the later one wins when several start on the same page. */
    private static BookSection sectionFor(List<BookSection> sections, int pdfPage) {
        BookSection best = null;
        for (BookSection s : sections) {
            if (s.getStartPage() <= pdfPage && pdfPage <= s.getEndPage()) {
                best = s;
            }
        }
        return best;
    }

    private static BookPage toPage(Book book, PageContent p, BookSection section) {
        BookPage page = new BookPage();
        page.setBook(book);
        page.setPageNumber(p.pdfPage());
        page.setSourcePdfPage(p.pdfPage());
        page.setMarkdownContent(p.rawText());
        page.setMarkdownClean(p.cleanedText());
        page.setPrintedPageLabel(truncate(p.printedPageLabel(), 20));
        page.setRunningHeader(truncate(p.runningHeader(), 500));
        page.setPageKind(p.imageOnly() ? PageKind.IMAGE_ONLY : p.cleanedText().isBlank() ? PageKind.BLANK : PageKind.BODY);
        page.setStatus(OcrStatus.COMPLETED);
        page.setOcrModel("text-layer");
        page.setPromptVersion("native-v1");
        page.setSection(section);
        return page;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static StructureSource toSource(DetectionSource d) {
        return switch (d) {
            case EMBEDDED_OUTLINE -> StructureSource.PDF_OUTLINE;
            case PRINTED_TOC -> StructureSource.TEXT_LAYER;
            case HEADING_DETECTION -> StructureSource.HEADINGS;
            case NONE -> StructureSource.TEXT_LAYER;
        };
    }

    private String toJson(BookExtractionResult r) {
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("structureDetection", r.structureDetection());
        review.put("toc", r.toc());
        review.put("warnings", r.warnings());
        try {
            return objectMapper.writeValueAsString(review);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
