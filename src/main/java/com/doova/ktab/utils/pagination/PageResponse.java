package com.doova.ktab.utils.pagination;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    public static <T> PageResponse<T> fromPage(Page<T> page) {
        Objects.requireNonNull(page, "page must not be null");

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
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must be greater than or equal to 0");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }

        return new PageResponse<>(
                Collections.emptyList(),
                pageNumber,
                pageSize,
                0,
                0,
                true
        );
    }
}
