package com.doova.ktab.dto.book;

public record InBookTextSearchResponse(
        Long bookId,
        int pageNumber,
        String snippet,
        int matchCount,
        int totalPageWords
) {}
