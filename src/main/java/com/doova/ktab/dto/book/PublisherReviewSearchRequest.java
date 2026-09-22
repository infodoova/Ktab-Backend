package com.doova.ktab.dto.book;

import com.doova.ktab.enums.status.BookStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublisherReviewSearchRequest(
        String q,
        BookStatus status,
        Long authorId,
        Long mainGenreId,
        Long subGenreId,
        String language,
        Instant submittedAfter,
        Instant submittedBefore,
        @Min(value = 0, message = "{validation.pagination.page.min}")
        int page,
        @Min(value = 1, message = "{validation.pagination.size.min}")
        @Max(value = 100, message = "{validation.pagination.size.max}")
        int size,
        String sortBy,
        Sort.Direction sortDirection
) {
    public PublisherReviewSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 10;
        if (status == null) status = BookStatus.UNDER_REVIEW;
        if (sortDirection == null) sortDirection = Sort.Direction.ASC;
        if (sortBy == null || sortBy.isBlank()) sortBy = "submittedAt";
    }
}
