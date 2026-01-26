package com.doova.ktab.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collections;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookSearchRequestDto(String title, List<Long> mainGenreIds, List<Long> subGenreIds, Integer age,
                                   BigDecimal minAverageRating, int page, int size) {
    public BookSearchRequestDto {
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
        if (mainGenreIds == null) mainGenreIds = Collections.emptyList();
        if (subGenreIds == null) subGenreIds = Collections.emptyList();
    }
}