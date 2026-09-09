package com.doova.ktab.utils.pagination;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;

    /**
     * Creates an empty PageResponse for a given requested page and size.
     * This is useful when the requested page number is out of bounds
     * or no results were found for the query.
     *
     * @param pageNumber The requested page number.
     * @param pageSize   The requested page size.
     * @return A PageResponse object with empty content and zero total elements/pages.
     */
    public static <T> PageResponse<T> fromPage(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    public static <T> PageResponse<T> empty(int pageNumber, int pageSize) {
        return new PageResponse<>(
                Collections.emptyList(), // Content is an empty list
                pageNumber,              // Use the requested page number
                pageSize,                // Use the requested page size
                0,                       // totalElements is 0
                0,                       // totalPages is 0
                true                     // Always the last page when empty
        );
    }
}