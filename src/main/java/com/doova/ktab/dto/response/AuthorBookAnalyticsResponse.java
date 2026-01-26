package com.doova.ktab.dto.response;

import com.doova.ktab.enums.status.BookStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AuthorBookAnalyticsResponse(
        Long bookId,
        String title,
        BookStatus status,
        Instant publishDate,
        BigDecimal averageRating,
        Integer totalReviews,
        String mainGenre,
        long totalReaders,
        String coverImageUrl
) {}
