package com.doova.ktab.dto.analytics;

import java.math.BigDecimal;

public record AuthorAnalyticsResponse(
        long totalBooks,
        long totalReads,
        BigDecimal averageRating,
        long totalReviews
) {}
