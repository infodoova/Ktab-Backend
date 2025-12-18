package com.doova.ktab.api.dto.response;

import java.math.BigDecimal;

public record AuthorAnalyticsResponse(
        long totalBooks,
        long totalReads,
        BigDecimal averageRating,
        long totalReviews
) {}
