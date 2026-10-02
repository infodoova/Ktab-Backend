package com.doova.ktab.features.extraction;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.extraction.dto.*;
import com.doova.ktab.features.extraction.structure.ChapterBuilder;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Reconstructs a {@link BookExtractionResult} from persisted database tables (pages, sections, book),
 * providing the exact same format as the in-memory extraction pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookExtractionQueryService {

    private final BookRepository bookRepository;
    private final BookPageRepository pageRepository;
    private final BookSectionRepository sectionRepository;
    private final ChapterBuilder chapterBuilder;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BookExtractionResult getExtractedBook(Long bookId, boolean includePages) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.READER_BOOK_NOT_FOUND));

        List<BookPage> dbPages = pageRepository.findByBookIdOrderByPageNumberAsc(bookId);
        List<BookSection> dbSections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);

        String authorName = book.getAuthor() != null ? book.getAuthor().getFullName() : book.getCustomAuthorName();
        int pageCount = book.getPageCount() != null ? book.getPageCount() : dbPages.size();
        BookMetadata metadata = new BookMetadata(
                book.getTitle(),
                authorName,
                null,
                null,
                book.getLanguage() != null ? book.getLanguage() : "ar",
                pageCount
        );

        StructureDetection structureDetection = parseStructureDetection(book);
        List<ExtractionWarning> warnings = parseWarnings(book);

        List<PageContent> pageContents = new ArrayList<>();
        for (BookPage p : dbPages) {
            String clean = p.getMarkdownClean() != null ? p.getMarkdownClean() : p.getMarkdownContent();
            if (clean == null) {
                clean = "";
            }
            List<String> lines = clean.isBlank() ? List.of() : Arrays.asList(clean.split("\n", -1));
            pageContents.add(new PageContent(
                    p.getPageNumber(),
                    p.getMarkdownContent() != null ? p.getMarkdownContent() : "",
                    clean,
                    p.getPrintedPageLabel(),
                    p.getRunningHeader(),
                    lines,
                    p.getPageKind() == PageKind.IMAGE_ONLY
            ));
        }

        List<TocEntry> toc = buildToc(dbSections);
        List<Chapter> chapters;
        if (!toc.isEmpty()) {
            chapters = chapterBuilder.build(toc, pageContents);
        } else if (!pageContents.isEmpty()) {
            String title = book.getTitle() != null && !book.getTitle().isBlank() ? book.getTitle() : "الكتاب";
            StringBuilder text = new StringBuilder();
            for (PageContent p : pageContents) {
                if (!p.cleanedText().isBlank()) {
                    if (text.length() > 0) {
                        text.append("\n\n");
                    }
                    text.append(p.cleanedText());
                }
            }
            chapters = List.of(new Chapter(1, title, 1, pageContents.size(), TocEntryType.CHAPTER,
                    text.toString(), pageContents, List.of()));
        } else {
            chapters = List.of();
        }

        BookExtractionResult result = new BookExtractionResult(metadata, structureDetection, toc, chapters, pageContents, warnings);
        return includePages ? result : result.compact();
    }

    private List<TocEntry> buildToc(List<BookSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }
        Map<Long, List<BookSection>> byParent = new LinkedHashMap<>();
        List<BookSection> roots = new ArrayList<>();
        for (BookSection s : sections) {
            Long parentId = s.getParent() != null ? s.getParent().getId() : null;
            if (parentId == null) {
                roots.add(s);
            } else {
                byParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(s);
            }
        }
        List<TocEntry> toc = new ArrayList<>();
        for (BookSection root : roots) {
            toc.add(toTocEntry(root, byParent));
        }
        return toc;
    }

    private TocEntry toTocEntry(BookSection section, Map<Long, List<BookSection>> byParent) {
        List<BookSection> childSections = byParent.getOrDefault(section.getId(), List.of());
        List<TocEntry> children = new ArrayList<>();
        for (BookSection child : childSections) {
            children.add(toTocEntry(child, byParent));
        }
        int start = section.getStartPage() != null ? section.getStartPage() : 1;
        int end = section.getEndPage() != null ? section.getEndPage() : start;
        return new TocEntry(section.getTitle(), section.getLevel(), start, end, toTocEntryType(section.getSectionType()), children);
    }

    private static TocEntryType toTocEntryType(SectionType type) {
        if (type == null) {
            return TocEntryType.OTHER;
        }
        return switch (type) {
            case INTRODUCTION, FOREWORD, PREFACE -> TocEntryType.INTRODUCTION;
            case CHAPTER, PART, FRONT_MATTER -> TocEntryType.CHAPTER;
            case SUBSECTION -> TocEntryType.SUBSECTION;
            case CONCLUSION -> TocEntryType.CONCLUSION;
            default -> TocEntryType.OTHER;
        };
    }

    private StructureDetection parseStructureDetection(Book book) {
        if (book.getTocRaw() != null && !book.getTocRaw().isBlank()) {
            try {
                Map<String, Object> map = objectMapper.readValue(book.getTocRaw(), new TypeReference<>() {});
                if (map.containsKey("structureDetection")) {
                    return objectMapper.convertValue(map.get("structureDetection"), StructureDetection.class);
                }
            } catch (Exception e) {
                log.debug("Could not parse structureDetection from tocRaw for bookId={}: {}", book.getId(), e.getMessage());
            }
        }
        DetectionSource source = toDetectionSource(book.getStructureSource());
        return new StructureDetection(source, 1.0);
    }

    private List<ExtractionWarning> parseWarnings(Book book) {
        if (book.getTocRaw() != null && !book.getTocRaw().isBlank()) {
            try {
                Map<String, Object> map = objectMapper.readValue(book.getTocRaw(), new TypeReference<>() {});
                if (map.containsKey("warnings")) {
                    return objectMapper.convertValue(map.get("warnings"), new TypeReference<List<ExtractionWarning>>() {});
                }
            } catch (Exception e) {
                log.debug("Could not parse warnings from tocRaw for bookId={}: {}", book.getId(), e.getMessage());
            }
        }
        return List.of();
    }

    private static DetectionSource toDetectionSource(StructureSource source) {
        if (source == null) {
            return DetectionSource.NONE;
        }
        return switch (source) {
            case PDF_OUTLINE -> DetectionSource.EMBEDDED_OUTLINE;
            case TEXT_LAYER -> DetectionSource.PRINTED_TOC;
            case HEADINGS -> DetectionSource.HEADING_DETECTION;
            default -> DetectionSource.NONE;
        };
    }
}
