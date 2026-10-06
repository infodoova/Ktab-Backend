package com.doova.ktab.features.extraction;

import com.doova.ktab.dto.book.*;
import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.extraction.dto.*;
import com.doova.ktab.features.extraction.structure.ChapterBuilder;
import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;
import com.doova.ktab.features.ocr.text.SectionClassifier;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    // ============================================================================================
    // PURE BODY READER PAGINATION (EXCLUDING INTRODUCTIONS, BIBLIOGRAPHIES, APPENDIXES)
    // ============================================================================================

    private static final Set<SectionType> EXCLUDED_SECTION_TYPES = Set.of(
            SectionType.FRONT_MATTER,
            SectionType.DEDICATION,
            SectionType.FOREWORD,
            SectionType.PREFACE,
            SectionType.INTRODUCTION,
            SectionType.APPENDIX,
            SectionType.BIBLIOGRAPHY,
            SectionType.INDEX,
            SectionType.GLOSSARY
    );

    private static final Set<PageKind> EXCLUDED_PAGE_KINDS = Set.of(
            PageKind.COVER,
            PageKind.TITLE_PAGE,
            PageKind.COPYRIGHT,
            PageKind.BLANK,
            PageKind.TOC,
            PageKind.INDEX,
            PageKind.IMAGE_ONLY,
            PageKind.UNREADABLE
    );

    private record WordToken(String word, String spacingBefore, String spacingAfter, int pageNumber, String chapterTitle) {}

    /** Source pages visible in the reader, shared with Talk to Book retrieval. */
    @Transactional(readOnly = true)
    public List<BookPage> getReaderSourcePages(Long bookId) {
        List<BookSection> sections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);
        return filterPureBodyPages(pageRepository.findByBookIdOrderByPageNumberAsc(bookId), sections);
    }

    /** Finds a citation in the exact filtered word stream used by reader pagination. */
    @Transactional(readOnly = true)
    public Map<String, Object> locateReaderSnippet(Long bookId, String snippet, int wordsPerPage) {
        if (snippet == null || snippet.isBlank() || snippet.length() > 2000) {
            return Map.of("found", false);
        }
        bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.READER_BOOK_NOT_FOUND));
        int pageSize = wordsPerPage > 0 ? wordsPerPage : 80;
        List<BookSection> sections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);
        List<WordToken> words = extractWordTokens(
                filterPureBodyPages(pageRepository.findByBookIdOrderByPageNumberAsc(bookId), sections), sections);
        List<String> needle = Arrays.stream(snippet.split("\\s+"))
                .map(BookExtractionQueryService::normalizeCitationWord)
                .filter(token -> !token.isEmpty()).toList();
        if (needle.isEmpty()) return Map.of("found", false);
        List<String> haystack = words.stream().map(word -> normalizeCitationWord(word.word())).toList();

        int match = findCitationSequence(haystack, needle);
        // OCR can vary within a citation; a longer consecutive anchor still locates the passage.
        if (match < 0 && needle.size() >= 4) {
            int anchorLength = Math.min(6, needle.size());
            for (int offset = 0; offset <= needle.size() - anchorLength && match < 0; offset++) {
                int anchorIndex = findCitationSequence(haystack,
                        needle.subList(offset, offset + anchorLength));
                if (anchorIndex >= 0) match = Math.max(0, anchorIndex - offset);
            }
        }
        if (match < 0) return Map.of("found", false);
        // Open the page where the citation begins; the rest may continue on the next page.
        int targetPage = match / pageSize + 1;
        int visibleEnd = Math.min(words.size() - 1,
                Math.min(match + needle.size() - 1, targetPage * pageSize - 1));
        return Map.of("found", true, "page", targetPage,
                "startWordIndex", match, "endWordIndex", visibleEnd,
                "pdfPageNumber", words.get(match).pageNumber());
    }

    /** Maps a physical PDF page in legacy citations to a reader page. */
    @Transactional(readOnly = true)
    public Map<String, Object> locateReaderPdfPage(Long bookId, int pdfPageNumber, int wordsPerPage) {
        if (pdfPageNumber < 1) return Map.of("found", false);
        bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.READER_BOOK_NOT_FOUND));
        List<BookSection> sections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);
        List<WordToken> words = extractWordTokens(
                filterPureBodyPages(pageRepository.findByBookIdOrderByPageNumberAsc(bookId), sections), sections);
        for (int index = 0; index < words.size(); index++) {
            if (words.get(index).pageNumber() == pdfPageNumber) {
                return Map.of("found", true, "page", index / Math.max(1, wordsPerPage) + 1,
                        "startWordIndex", index, "pdfPageNumber", pdfPageNumber);
            }
        }
        return Map.of("found", false);
    }

    private static int findCitationSequence(List<String> words, List<String> needle) {
        for (int i = 0; i <= words.size() - needle.size(); i++) {
            boolean matches = true;
            for (int j = 0; j < needle.size(); j++) {
                if (!words.get(i + j).equals(needle.get(j))) {
                    matches = false;
                    break;
                }
            }
            if (matches) return i;
        }
        return -1;
    }

    private static String normalizeCitationWord(String word) {
        return word.replaceAll("[\u064B-\u065F\u0670\u06D6-\u06ED\u0640]", "")
                .replaceAll("[أإآٱ]", "ا").replace('ى', 'ي').replace('ة', 'ه')
                .replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }


    /**
     * Slices book text into auto-incremented pages of ~N words (default 80), starting strictly
     * from core body chapters and excluding introductions, forewords, dedications, prefaces,
     * bibliographies, indexes, and appendixes.
     */
    @Transactional(readOnly = true)
    public ReaderPageResponse getReaderPage(Long bookId, int page, int wordsPerPage) {
        if (page < 1) {
            page = 1;
        }
        if (wordsPerPage <= 0) {
            wordsPerPage = 80;
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.READER_BOOK_NOT_FOUND));

        List<BookPage> dbPages = pageRepository.findByBookIdOrderByPageNumberAsc(bookId);
        List<BookSection> dbSections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);

        List<BookPage> bodyPages = filterPureBodyPages(dbPages, dbSections);
        List<WordToken> allWords = extractWordTokens(bodyPages, dbSections);

        int totalWords = allWords.size();
        int totalPages = totalWords > 0 ? (int) Math.ceil((double) totalWords / wordsPerPage) : 0;

        if (totalWords == 0) {
            return new ReaderPageResponse(
                    bookId,
                    book.getTitle(),
                    1,
                    0,
                    wordsPerPage,
                    0,
                    0L,
                    0L,
                    0L,
                    0.0,
                    null,
                    null,
                    "",
                    true,
                    true,
                    false,
                    false
            );
        }

        if (page > totalPages) {
            page = totalPages;
        }

        long startWordIndex = (long) (page - 1) * wordsPerPage;
        long endWordIndex = Math.min((long) page * wordsPerPage, (long) totalWords);

        StringBuilder contentBuilder = new StringBuilder();
        int wordCountOnPage = 0;
        String chapterTitle = null;
        Integer pdfPageNumber = null;

        for (int i = (int) startWordIndex; i < (int) endWordIndex; i++) {
            WordToken token = allWords.get(i);
            if (wordCountOnPage == 0) {
                pdfPageNumber = token.pageNumber();
                chapterTitle = token.chapterTitle();
            } else if (token.pageNumber() != allWords.get(i - 1).pageNumber()) {
                contentBuilder.append("\n\n");
            }
            contentBuilder.append(token.spacingBefore());
            contentBuilder.append(token.word());
            contentBuilder.append(token.spacingAfter());
            wordCountOnPage++;
        }

        if (chapterTitle == null && pdfPageNumber != null) {
            chapterTitle = resolveChapterTitleForPage(pdfPageNumber, dbSections);
        }

        double progress = totalPages > 0
                ? Math.min(100.0, Math.round(((double) page / totalPages) * 10000.0) / 100.0)
                : 0.0;

        return new ReaderPageResponse(
                bookId,
                book.getTitle(),
                page,
                totalPages,
                wordsPerPage,
                wordCountOnPage,
                startWordIndex,
                endWordIndex,
                totalWords,
                progress,
                chapterTitle,
                pdfPageNumber,
                contentBuilder.toString(),
                page == 1,
                page >= totalPages,
                page < totalPages,
                page > 1
        );
    }

    public boolean isAppendixSection(BookSection s) {
        if (s == null) {
            return false;
        }
        SectionType type = s.getSectionType();
        if (type == SectionType.APPENDIX || type == SectionType.BIBLIOGRAPHY ||
                type == SectionType.INDEX || type == SectionType.GLOSSARY) {
            return true;
        }
        String title = s.getTitle();
        if (title != null && !title.isBlank()) {
            SectionType classified = SectionClassifier.classify(title);
            if (classified == SectionType.APPENDIX || classified == SectionType.BIBLIOGRAPHY ||
                    classified == SectionType.INDEX || classified == SectionType.GLOSSARY) {
                return true;
            }
            String norm = ArabicTextNormalizer.normalize(title);
            if (norm.contains("ملحق") || norm.contains("ملاحق") ||
                    norm.contains("مراجع") || norm.contains("مصادر ومراجع") ||
                    norm.contains("المصادر والمراجع") || norm.contains("قائمه المراجع") ||
                    norm.contains("ثبت المراجع") || norm.contains("ثبت المصادر") ||
                    norm.contains("فهرس المصطلحات") || norm.contains("فهرس الاعلام") ||
                    norm.contains("فهرس الايات") || norm.contains("فهرس الاحاديث") ||
                    norm.contains("فهرس الاماكن") || norm.contains("كشاف")) {
                return true;
            }
        }
        return false;
    }

    private boolean isReaderExcludedSection(BookSection section) {
        if (section == null) {
            return false;
        }
        if (isAppendixSection(section)) {
            return true;
        }
        if (section.getSectionType() != null && EXCLUDED_SECTION_TYPES.contains(section.getSectionType())) {
            return true;
        }
        return section.getTitle() != null
                && EXCLUDED_SECTION_TYPES.contains(SectionClassifier.classify(section.getTitle()));
    }

    private List<BookPage> filterPureBodyPages(List<BookPage> dbPages, List<BookSection> dbSections) {
        if (dbPages == null || dbPages.isEmpty()) {
            return List.of();
        }

        Set<Integer> appendixPageNumbers = new HashSet<>();
        Set<Integer> excludedSectionPageNumbers = new HashSet<>();
        Integer minAppendixStartPage = null;
        Integer minBodyStartPage = null;

        if (dbSections != null) {
            for (BookSection s : dbSections) {
                if (s.getStartPage() == null) {
                    continue;
                }

                int end = s.getEndPage() != null ? s.getEndPage() : s.getStartPage();
                boolean appendix = isAppendixSection(s);
                boolean excluded = isReaderExcludedSection(s);

                if (excluded) {
                    for (int p = s.getStartPage(); p <= end; p++) {
                        excludedSectionPageNumbers.add(p);
                    }
                }

                if (appendix) {
                    if (s.getStartPage() >= 15
                            && (minAppendixStartPage == null || s.getStartPage() < minAppendixStartPage)) {
                        minAppendixStartPage = s.getStartPage();
                    }
                    for (int p = s.getStartPage(); p <= end; p++) {
                        appendixPageNumbers.add(p);
                    }
                } else if (!excluded
                        && (minBodyStartPage == null || s.getStartPage() < minBodyStartPage)) {
                    minBodyStartPage = s.getStartPage();
                }
            }
        }

        List<BookPage> result = new ArrayList<>();
        for (BookPage p : dbPages) {
            if (isEligibleBodyPage(
                    p, appendixPageNumbers, excludedSectionPageNumbers, minAppendixStartPage, minBodyStartPage
            )) {
                result.add(p);
            }
        }
        return result;
    }

    private boolean isEligibleBodyPage(
            BookPage p,
            Set<Integer> appendixPageNumbers,
            Set<Integer> excludedSectionPageNumbers,
            Integer minAppendixStartPage,
            Integer minBodyStartPage
    ) {
        if (p.getPageKind() != null && EXCLUDED_PAGE_KINDS.contains(p.getPageKind())) {
            return false;
        }

        String text = p.getMarkdownClean() != null && !p.getMarkdownClean().isBlank()
                ? p.getMarkdownClean()
                : p.getMarkdownContent();
        if (text == null || text.isBlank()) {
            return false;
        }

        int pageNum = p.getPageNumber();

        if (excludedSectionPageNumbers.contains(pageNum)) {
            return false;
        }

        // Exclude pages before the first actual body chapter.
        if (minBodyStartPage != null && pageNum < minBodyStartPage) {
            return false;
        }

        // 2. Exclude appendixes, bibliographies, and glossaries at the end
        if (minAppendixStartPage != null && pageNum >= minAppendixStartPage) {
            return false;
        }

        if (appendixPageNumbers != null && appendixPageNumbers.contains(pageNum)) {
            return false;
        }

        // 3. Heuristic: Exclude table of contents or index pages with dot leaders
        if (isTocPage(text)) {
            return false;
        }

        return true;
    }

    private boolean isTocPage(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        Matcher m = Pattern.compile("(\\.{3,}|…{2,}|_{4,})").matcher(text);
        int dotLeaderCount = 0;
        while (m.find()) {
            dotLeaderCount++;
            if (dotLeaderCount >= 3) {
                return true;
            }
        }

        int nonBlankLines = 0;
        int numberedEntryLines = 0;
        Pattern trailingPageNumber = Pattern.compile(".*[\\s.·…_-]+[0-9\\u0660-\\u0669\\u06F0-\\u06F9]{1,4}$");
        for (String line : text.split("\\R")) {
            String candidate = line.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            nonBlankLines++;
            if (trailingPageNumber.matcher(candidate).matches()) {
                numberedEntryLines++;
            }
        }
        return numberedEntryLines >= 4 && numberedEntryLines * 2 >= nonBlankLines;
    }

    @Transactional(readOnly = true)
    public BookNavigatorResponse getNavigator(Long bookId, int wordsPerPage) {
        if (wordsPerPage <= 0) {
            wordsPerPage = 80;
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.READER_BOOK_NOT_FOUND));

        List<BookPage> dbPages = pageRepository.findByBookIdOrderByPageNumberAsc(bookId);
        List<BookSection> dbSections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);

        List<BookPage> bodyPages = filterPureBodyPages(dbPages, dbSections);
        List<WordToken> allWords = extractWordTokens(bodyPages, dbSections);

        Map<Integer, Integer> pdfPageToReaderPage = new HashMap<>();
        for (int i = 0; i < allWords.size(); i++) {
            int pdfPage = allWords.get(i).pageNumber();
            int readerPage = (i / wordsPerPage) + 1;
            pdfPageToReaderPage.putIfAbsent(pdfPage, readerPage);
        }

        List<BookSectionNodeDto> sectionNodes = buildSectionNodes(dbSections, pdfPageToReaderPage);

        return new BookNavigatorResponse(
                bookId,
                book.getTitle(),
                dbSections.size(),
                sectionNodes
        );
    }

    @Transactional(readOnly = true)
    public com.doova.ktab.dto.book.BookSectionContentResponse getSectionContent(Long bookId, Long sectionId) {
        BookSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.RESOURCE_NOT_FOUND));

        if (section.getBook() == null || !section.getBook().getId().equals(bookId)) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        int startPage = section.getStartPage() != null ? section.getStartPage() : 1;
        int endPage = section.getEndPage() != null ? section.getEndPage() : startPage;

        List<BookPage> pages = pageRepository.findByBookIdAndPageNumberBetweenOrderByPageNumberAsc(bookId, startPage, endPage);

        StringBuilder sb = new StringBuilder();
        for (BookPage p : pages) {
            String clean = p.getMarkdownClean() != null && !p.getMarkdownClean().isBlank()
                    ? p.getMarkdownClean()
                    : p.getMarkdownContent();
            if (clean != null && !clean.isBlank()) {
                if (sb.length() > 0) {
                    sb.append("\n\n---\n\n");
                }
                sb.append(clean);
            }
        }

        return new com.doova.ktab.dto.book.BookSectionContentResponse(
                section.getId(),
                bookId,
                section.getTitle(),
                section.getSectionType() != null ? section.getSectionType().name() : "OTHER",
                sb.toString().trim()
        );
    }

    private List<BookSectionNodeDto> buildSectionNodes(
            List<BookSection> sections,
            Map<Integer, Integer> pdfPageToReaderPage
    ) {
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

        List<BookSectionNodeDto> result = new ArrayList<>();
        for (BookSection root : roots) {
            result.add(toSectionNodeDto(root, byParent, pdfPageToReaderPage));
        }
        return result;
    }

    private BookSectionNodeDto toSectionNodeDto(
            BookSection s,
            Map<Long, List<BookSection>> byParent,
            Map<Integer, Integer> pdfPageToReaderPage
    ) {
        List<BookSection> children = byParent.getOrDefault(s.getId(), List.of());
        List<BookSectionNodeDto> childDtos = new ArrayList<>();
        for (BookSection c : children) {
            childDtos.add(toSectionNodeDto(c, byParent, pdfPageToReaderPage));
        }

        boolean isAppendix = isAppendixSection(s);
        Integer readerPage = null;

        // ONLY compute readerPage for non-appendix body sections!
        if (!isAppendix && s.getStartPage() != null) {
            readerPage = pdfPageToReaderPage.get(s.getStartPage());
            if (readerPage == null && s.getEndPage() != null) {
                for (int p = s.getStartPage() + 1; p <= s.getEndPage(); p++) {
                    Integer found = pdfPageToReaderPage.get(p);
                    if (found != null) {
                        readerPage = found;
                        break;
                    }
                }
            }
        }

        return new BookSectionNodeDto(
                s.getId(),
                s.getTitle(),
                readerPage,
                isAppendix,
                childDtos
        );
    }

    private List<WordToken> extractWordTokens(List<BookPage> bodyPages, List<BookSection> sections) {
        List<WordToken> tokens = new ArrayList<>();
        Pattern wordPattern = Pattern.compile("\\S+");

        for (BookPage p : bodyPages) {
            String text = p.getMarkdownClean() != null && !p.getMarkdownClean().isBlank()
                    ? p.getMarkdownClean()
                    : p.getMarkdownContent();
            if (text == null || text.isBlank()) {
                continue;
            }

            // Reflow PDF line wraps into spaces while keeping paragraph breaks.
            text = text.replaceAll("(\\.{3,}|…{2,}|_{4,})", " ")
                    .replaceAll("\\r\\n?", "\n")
                    .replaceAll("(?<!\\n)\\n(?!\\n)", " ");

            String chapterTitle = resolveChapterTitleForPage(p, sections);
            Matcher matcher = wordPattern.matcher(text);
            int prevEnd = 0;
            int firstTokenIndex = tokens.size();

            while (matcher.find()) {
                tokens.add(new WordToken(
                        matcher.group(),
                        text.substring(prevEnd, matcher.start()),
                        "",
                        p.getPageNumber(),
                        chapterTitle
                ));
                prevEnd = matcher.end();
            }

            if (tokens.size() > firstTokenIndex) {
                int lastIndex = tokens.size() - 1;
                WordToken last = tokens.get(lastIndex);
                tokens.set(lastIndex, new WordToken(
                        last.word(),
                        last.spacingBefore(),
                        text.substring(prevEnd),
                        last.pageNumber(),
                        last.chapterTitle()
                ));
            }
        }
        return tokens;
    }

    private String resolveChapterTitleForPage(BookPage page, List<BookSection> sections) {
        if (page.getSection() != null && page.getSection().getTitle() != null && !page.getSection().getTitle().isBlank()) {
            return page.getSection().getTitle();
        }
        return resolveChapterTitleForPage(page.getPageNumber(), sections);
    }

    private String resolveChapterTitleForPage(int pageNum, List<BookSection> sections) {
        if (sections == null) return null;
        for (BookSection s : sections) {
            if (s.getStartPage() != null && s.getEndPage() != null
                    && pageNum >= s.getStartPage() && pageNum <= s.getEndPage()) {
                if (s.getTitle() != null && !s.getTitle().isBlank()) {
                    return s.getTitle();
                }
            }
        }
        return null;
    }
}
