package com.doova.ktab.dto.book;

import com.doova.ktab.enums.book.BookSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookSearchRequestDto(
        String title,
        List<Long> mainGenreIds,
        List<Long> subGenreIds,
        Integer age,
        BigDecimal minAverageRating,
        BookSource bookSource,
        int page,
        int size) {

    public BookSearchRequestDto(String title, List<Long> mainGenreIds, List<Long> subGenreIds, Integer age,
                                BigDecimal minAverageRating, int page, int size) {
        this(title, mainGenreIds, subGenreIds, age, minAverageRating, BookSource.AUTHOR, page, size);
    }

    public BookSearchRequestDto {
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
        if (mainGenreIds == null) mainGenreIds = Collections.emptyList();
        if (subGenreIds == null) subGenreIds = Collections.emptyList();
        if (bookSource == null) bookSource = BookSource.AUTHOR;
    }
}
