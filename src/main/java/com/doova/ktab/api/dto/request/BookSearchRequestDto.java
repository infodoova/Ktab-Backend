package com.doova.ktab.api.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collections; // Import Collections for safety

/**
 * Record for carrying dynamic book search criteria and pagination information.
 * Genre is now a List<String> to support multiple genre searching.
 */
public record BookSearchRequestDto(String title, List<String> genres,
                                   // <-- Changed from String genre to List<String> genres
                                   Integer age,
                                   // Minimum average rating the book must have
                                   BigDecimal minAverageRating,
                                   // Pagination
                                   int page, int size) {
    // Compact constructor for validation and default values
    public BookSearchRequestDto {
        // Ensure size is positive and page is non-negative
        if (size <= 0) {
            size = 10;
        }
        if (page < 0) {
            page = 0;
        }

        // Ensure genres list is not null, providing an empty list if null is passed
        // This makes handling easier in the service layer.
        if (genres == null) {
            genres = Collections.emptyList();
        }
    }

    // Convenience constructor for common use cases (defaults page=0, size=10)
    public BookSearchRequestDto(String title, List<String> genres, Integer age, BigDecimal minAverageRating) {
        this(title, genres, age, minAverageRating, 0, 10);
    }
}