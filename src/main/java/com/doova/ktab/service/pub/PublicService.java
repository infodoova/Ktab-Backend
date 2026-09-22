package com.doova.ktab.service.pub;

import com.doova.ktab.dto.book.BookCoverResponse;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto.RoleMetadataDto;
import com.doova.ktab.utils.pagination.PageResponse;

import java.util.List;

/**
 * Facade service for public (unauthenticated) catalog endpoints.
 * <p>
 * All methods are read-only; implementations must annotate with
 * {@code @Transactional(readOnly = true)}.
 */
public interface PublicService {

    /**
     * Returns a paginated list of published book covers for the public catalog.
     *
     * @param page zero-based page index
     * @param size number of items per page
     * @return paginated {@link BookCoverResponse} list
     */
    PageResponse<BookCoverResponse> getBookCovers(int page, int size);

    /**
     * Returns the user-registration roles available for public signup
     * (Author and Reader only).
     *
     * @return list of {@link RoleMetadataDto}
     */
    List<RoleMetadataDto> getRoles();
}
