package com.doova.ktab.features.story.dto;

import com.doova.ktab.features.story.enums.StoryLens;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StorySearchRequest(
        String q,
        String genre,
        StoryLens lens,
        Long authorId,
        @Min(value = 0, message = "{validation.pagination.page.min}")
        int page,
        @Min(value = 1, message = "{validation.pagination.size.min}")
        @Max(value = 100, message = "{validation.pagination.size.max}")
        int size,
        String sortBy,
        Sort.Direction sortDirection
) {
    public StorySearchRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 10;
        if (sortDirection == null) sortDirection = Sort.Direction.DESC;
        if (sortBy == null || sortBy.isBlank()) sortBy = "createdAt";
    }
}
