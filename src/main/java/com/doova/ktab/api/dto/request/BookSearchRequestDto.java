package com.doova.ktab.api.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collections;

public record BookSearchRequestDto(

        String title,

        List<Long> mainGenreIds, List<Long> subGenreIds,

        Integer age, BigDecimal minAverageRating,

        int page, int size) {

    // Compact constructor (validation + defaults)
    public BookSearchRequestDto {

        if (page < 0) {
            page = 0;
        }

        if (size <= 0) {
            size = 10;
        }

        if (mainGenreIds == null) {
            mainGenreIds = Collections.emptyList();
        }

        if (subGenreIds == null) {
            subGenreIds = Collections.emptyList();
        }
    }

    // Convenience constructor (no pagination provided)
    public BookSearchRequestDto(String title, List<Long> mainGenreIds, List<Long> subGenreIds, Integer age, BigDecimal minAverageRating) {
        this(title, mainGenreIds, subGenreIds, age, minAverageRating, 0, 10);
    }
}
