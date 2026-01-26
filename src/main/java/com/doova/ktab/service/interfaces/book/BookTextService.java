package com.doova.ktab.service.interfaces.book;

import com.doova.ktab.dto.response.BookStatsResponse;
import com.doova.ktab.dto.response.TextRangeResponse;

public interface BookTextService {

    String getFullText(Long bookId);

    TextRangeResponse getTextByCharacterRange(Long bookId, int start, int end);

    String getTextByPageRange(Long bookId, int from, int to);

    BookStatsResponse getBookStats(Long bookId);

    TextRangeResponse getTextByWordRange(Long bookId, int startWord, int endWord);
}
