package com.doova.ktab.dto.book;

import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.enums.status.OcrStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LibrarianBookSearchRequest(
        String q,
        String customAuthorName,
        String language,
        Long mainGenreId,
        Long subGenreId,
        Boolean hasAudio,
        OcrStatus ocrStatus,
        BookStatus status,
        @Min(value = 0, message = "{validation.pagination.page.min}")
        int page,
        @Min(value = 1, message = "{validation.pagination.size.min}")
        @Max(value = 100, message = "{validation.pagination.size.max}")
        int size,
        String sortBy,
        Sort.Direction sortDirection
) {
    public LibrarianBookSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 10;
        if (sortDirection == null) sortDirection = Sort.Direction.DESC;
        if (sortBy == null || sortBy.isBlank()) sortBy = "createdAt";
    }
}
