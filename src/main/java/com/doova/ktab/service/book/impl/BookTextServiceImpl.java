package com.doova.ktab.service.book.impl;

import com.doova.ktab.dto.book.BookStatsResponse;
import com.doova.ktab.dto.book.TextRangeResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.service.book.BookTextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BookTextServiceImpl implements BookTextService {

    private final BookPageRepository sectionRepository;

    @Override
    public String getFullText(Long bookId) {
        return sectionRepository.findByBookIdOrderByPageNumberAsc(bookId).stream().map(BookPage::getMarkdownContent).collect(Collectors.joining("\n\n"));
    }

    @Override
    public TextRangeResponse getTextByCharacterRange(Long bookId, int start, int end) {

        if (start < 0 || end <= start) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        StringBuilder result = new StringBuilder(end - start);
        int totalChars = 0;
        int globalIndex = 0;

        try (Stream<BookPage> stream = sectionRepository.streamByBookIdOrderByPageNumberAsc(bookId)) {

            for (BookPage section : (Iterable<BookPage>) stream::iterator) {

                String content = section.getMarkdownContent();
                int length = content.length();

                int sectionStart = globalIndex;
                int sectionEnd = globalIndex + length;
                totalChars += length;

                // Skip before range
                if (sectionEnd <= start) {
                    globalIndex = sectionEnd;
                    continue;
                }

                // Stop after range
                if (sectionStart >= end) {
                    break;
                }

                int sliceStart = Math.max(0, start - sectionStart);
                int sliceEnd = Math.min(length, end - sectionStart);

                if (sliceStart < sliceEnd) {
                    result.append(content, sliceStart, sliceEnd);
                }

                globalIndex = sectionEnd;

                if (globalIndex >= end) {
                    break;
                }
            }
        }

        int safeStart = Math.min(start, totalChars);
        int safeEnd = Math.min(end, totalChars);

        return new TextRangeResponse(result.toString(), safeStart, safeEnd, totalChars);
    }

    @Override
    public TextRangeResponse getTextByWordRange(Long bookId, int startWord, int endWord) {

        if (startWord < 0 || endWord <= startWord) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        StringBuilder result = new StringBuilder();
        int totalWords = 0;

        int globalWordIndex = 0;
        int globalCharIndex = 0;

        int rangeStartChar = -1;
        int rangeEndChar = -1;

        Pattern wordPattern = Pattern.compile("\\S+");

        try (Stream<BookPage> stream = sectionRepository.streamByBookIdOrderByPageNumberAsc(bookId)) {

            for (BookPage section : (Iterable<BookPage>) stream::iterator) {

                String content = section.getMarkdownContent();
                Matcher matcher = wordPattern.matcher(content);

                while (matcher.find()) {

                    int wordStartInSection = matcher.start();
                    int wordEndInSection = matcher.end();

                    int wordStartGlobalChar = globalCharIndex + wordStartInSection;
                    int wordEndGlobalChar = globalCharIndex + wordEndInSection;

                    // entering range
                    if (globalWordIndex == startWord) {
                        rangeStartChar = wordStartGlobalChar;
                    }

                    if (globalWordIndex >= startWord && globalWordIndex < endWord) {
                        // preserve original spacing
                        result.append(content, wordStartInSection, wordEndInSection).append(" ");
                    }

                    // exiting range
                    if (globalWordIndex == endWord) {
                        rangeEndChar = wordStartGlobalChar;
                        break;
                    }

                    globalWordIndex++;
                }

                totalWords = globalWordIndex;
                globalCharIndex += content.length();

                if (globalWordIndex >= endWord) {
                    break;
                }
            }
        }

        // clamp end if range reaches book end
        if (rangeStartChar != -1 && rangeEndChar == -1) {
            rangeEndChar = rangeStartChar + result.length();
        }

        if (rangeStartChar == -1) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        return new TextRangeResponse(result.toString().trim(), rangeStartChar, rangeEndChar, totalWords);
    }


    @Override
    public String getTextByPageRange(Long bookId, int from, int to) {
        if (from <= 0 || to < from) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        return sectionRepository.findByBookIdAndPageNumberBetweenOrderByPageNumberAsc(bookId, from, to).stream().map(BookPage::getMarkdownContent).collect(Collectors.joining("\n\n"));
    }

    @Override
    public BookStatsResponse getBookStats(Long bookId) {
        List<BookPage> sections = sectionRepository.findByBookIdOrderByPageNumberAsc(bookId);

        int totalChars = sections.stream().mapToInt(s -> s.getMarkdownContent().length()).sum();

        int totalWords = sectionRepository.getTotalWordCount(bookId);

        return new BookStatsResponse(sections.size(), totalWords, totalChars);
    }

    @Override
    public com.doova.ktab.utils.pagination.PageResponse<com.doova.ktab.dto.book.InBookTextSearchResponse> searchInBook(
            Long bookId,
            String keyword,
            org.springframework.data.domain.Pageable pageable
    ) {
        if (!org.springframework.util.StringUtils.hasText(keyword)) {
            return new com.doova.ktab.utils.pagination.PageResponse<>(
                    java.util.List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true
            );
        }

        org.springframework.data.domain.Page<BookPage> pages = sectionRepository.findAll(
                com.doova.ktab.specification.BookPageSpecification.forBookContent(bookId, keyword),
                pageable
        );

        java.util.List<com.doova.ktab.dto.book.InBookTextSearchResponse> dtoList = pages.getContent().stream().map(page -> {
            String snippet = com.doova.ktab.utils.search.ArabicSearchUtils.extractSnippet(page.getMarkdownContent(), keyword, 150);
            String normContent = com.doova.ktab.utils.search.ArabicSearchUtils.normalize(page.getMarkdownContent());
            String normKeyword = com.doova.ktab.utils.search.ArabicSearchUtils.normalize(keyword);
            int count = 0;
            int idx = 0;
            while (!normKeyword.isEmpty() && (idx = normContent.indexOf(normKeyword, idx)) != -1) {
                count++;
                idx += normKeyword.length();
            }
            return new com.doova.ktab.dto.book.InBookTextSearchResponse(
                    bookId,
                    page.getPageNumber(),
                    snippet,
                    count,
                    page.getWordCount()
            );
        }).toList();

        return new com.doova.ktab.utils.pagination.PageResponse<>(
                dtoList, pages.getNumber(), pages.getSize(), pages.getTotalElements(), pages.getTotalPages(), pages.isLast()
        );
    }
}
