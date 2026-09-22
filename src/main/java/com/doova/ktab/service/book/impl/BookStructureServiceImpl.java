package com.doova.ktab.service.book.impl;

import com.doova.ktab.dto.book.*;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.book.StructureStatus;
import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.doova.ktab.service.book.BookStructureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookStructureServiceImpl implements BookStructureService {

    private final BookRepository bookRepository;
    private final BookSectionRepository sectionRepository;
    private final BookPageRepository pageRepository;

    @Override
    @Transactional(readOnly = true)
    public BookStructureResponseDto getStructure(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NoSuchElementException("Book not found: " + bookId));

        List<BookSection> flatSections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);
        List<BookSectionItemDto> tree = buildDtoTree(flatSections);

        return new BookStructureResponseDto(
                book.getId(),
                book.getStructureStatus(),
                book.getStructureSource(),
                book.getPaginationMode(),
                book.getReadingDirection(),
                tree
        );
    }

    @Override
    @Transactional
    public BookStructureResponseDto updateStructure(Long bookId, BookStructureUpdateRequestDto request) {
        log.info("Updating manual structure for bookId={} with {} sections", bookId, request.sections().size());

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NoSuchElementException("Book not found: " + bookId));

        // 1. Delete previous sections
        sectionRepository.deleteByBook_Id(bookId);

        // 2. Insert new manual sections
        Map<Long, BookSection> tempIdMap = new HashMap<>();
        List<BookSection> sectionsToSave = new ArrayList<>();

        int sortOrder = 1;
        for (BookSectionUpdateDto dto : request.sections()) {
            BookSection section = new BookSection();
            section.setBook(book);
            section.setSectionType(dto.sectionType());
            section.setLevel(dto.level());
            section.setSortOrder(dto.sortOrder() > 0 ? dto.sortOrder() : sortOrder++);
            section.setDivisionLabel(dto.divisionLabel());
            section.setOrdinal(dto.ordinal());
            section.setTitle(dto.title());
            section.setTitleNormalized(ArabicTextNormalizer.normalize(dto.title()));
            section.setPrintedStartLabel(dto.printedStartLabel());
            section.setStartPage(dto.startPage());
            section.setEndPage(dto.endPage());
            section.setStartAnchor(dto.startAnchor());
            section.setSource(StructureSource.MANUAL);
            section.setConfidence(BigDecimal.ONE);
            section.setNeedsReview(false);

            if (dto.parentId() != null && tempIdMap.containsKey(dto.parentId())) {
                section.setParent(tempIdMap.get(dto.parentId()));
            }

            sectionsToSave.add(section);
            if (dto.id() != null) {
                tempIdMap.put(dto.id(), section);
            }
        }

        List<BookSection> saved = sectionRepository.saveAll(sectionsToSave);

        // 3. Re-assign pages to the new sections
        List<BookPage> pages = pageRepository.findByBookIdOrderByPageNumberAsc(bookId);
        for (BookPage page : pages) {
            int pageNum = page.getPageNumber();
            BookSection deepest = null;
            for (BookSection s : saved) {
                if (s.getStartPage() != null && s.getEndPage() != null) {
                    if (pageNum >= s.getStartPage() && pageNum <= s.getEndPage()) {
                        if (deepest == null || s.getLevel() >= deepest.getLevel()) {
                            deepest = s;
                        }
                    }
                }
            }
            page.setSection(deepest);
        }
        pageRepository.saveAll(pages);

        // 4. Update book status to MANUAL
        book.setStructureStatus(StructureStatus.MANUAL);
        book.setStructureSource(StructureSource.MANUAL);
        bookRepository.save(book);

        return getStructure(bookId);
    }

    @Override
    @Transactional
    public void patchSettings(Long bookId, BookSettingsPatchRequestDto request) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NoSuchElementException("Book not found: " + bookId));

        if (request.readingDirection() != null) {
            book.setReadingDirection(request.readingDirection());
        }

        bookRepository.save(book);
    }

    private List<BookSectionItemDto> buildDtoTree(List<BookSection> flatSections) {
        Map<Long, List<BookSectionItemDto>> childrenMap = new HashMap<>();
        List<BookSectionItemDto> roots = new ArrayList<>();

        for (BookSection s : flatSections) {
            BookSectionItemDto dto = new BookSectionItemDto(
                    s.getId(),
                    s.getParent() != null ? s.getParent().getId() : null,
                    s.getSectionType(),
                    s.getLevel(),
                    s.getSortOrder(),
                    s.getDivisionLabel(),
                    s.getOrdinal(),
                    s.getTitle(),
                    s.getPrintedStartLabel(),
                    s.getStartPage(),
                    s.getEndPage(),
                    s.getStartAnchor(),
                    s.getSource(),
                    s.getConfidence(),
                    s.isNeedsReview(),
                    new ArrayList<>()
            );

            if (s.getParent() == null) {
                roots.add(dto);
            } else {
                childrenMap.computeIfAbsent(s.getParent().getId(), k -> new ArrayList<>()).add(dto);
            }
        }

        // Attach children recursively
        attachChildren(roots, childrenMap);
        return roots;
    }

    private void attachChildren(List<BookSectionItemDto> currentLevel, Map<Long, List<BookSectionItemDto>> childrenMap) {
        for (BookSectionItemDto item : currentLevel) {
            List<BookSectionItemDto> kids = childrenMap.get(item.id());
            if (kids != null) {
                item.children().addAll(kids);
                attachChildren(kids, childrenMap);
            }
        }
    }
}
