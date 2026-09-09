package com.doova.ktab.service.book.impl;

import com.doova.ktab.dto.book.BookStatsResponse;
import com.doova.ktab.dto.book.TextRangeResponse;
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
    @Transactional(readOnly = true)
    public TextRangeResponse getTextByCharacterRange(Long bookId, int start, int end) {

        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Invalid character range");
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
    @Transactional(readOnly = true)
    public TextRangeResponse getTextByWordRange(Long bookId, int startWord, int endWord) {

        if (startWord < 0 || endWord <= startWord) {
            throw new IllegalArgumentException("Invalid word range");
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
            throw new IllegalArgumentException("Word range exceeds text length");
        }

        return new TextRangeResponse(result.toString().trim(), rangeStartChar, rangeEndChar, totalWords);
    }


    @Override
    public String getTextByPageRange(Long bookId, int from, int to) {
        if (from <= 0 || to < from) {
            throw new IllegalArgumentException("Invalid page range");
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
}
