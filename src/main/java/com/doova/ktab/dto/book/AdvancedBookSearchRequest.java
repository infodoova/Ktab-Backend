package com.doova.ktab.dto.book;

import com.doova.ktab.enums.book.BookSource;
import com.doova.ktab.enums.status.BookStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdvancedBookSearchRequest(
        String q,
        String title,
        String authorName,
        Long authorId,
        Long libraryOrganizationId,
        List<Long> mainGenreIds,
        List<Long> subGenreIds,
        Integer minAge,
        Integer maxAge,
        BigDecimal minRating,
        BigDecimal maxRating,
        Integer fromYear,
        Integer toYear,
        Boolean hasAudio,
        BookSource bookSource,
        BookStatus status,
        @Min(value = 0, message = "{validation.pagination.page.min}")
        int page,
        @Min(value = 1, message = "{validation.pagination.size.min}")
        @Max(value = 100, message = "{validation.pagination.size.max}")
        int size,
        String sortBy,
        Sort.Direction sortDirection
) {
    public AdvancedBookSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 10;
        if (mainGenreIds == null) mainGenreIds = Collections.emptyList();
        if (subGenreIds == null) subGenreIds = Collections.emptyList();
        if (sortDirection == null) sortDirection = Sort.Direction.DESC;
        if (sortBy == null || sortBy.isBlank()) sortBy = "averageRating";
    }
}
