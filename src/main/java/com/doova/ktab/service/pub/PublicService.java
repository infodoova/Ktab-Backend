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
     * Cover image URLs only, of the best-rated published books that have at least one review.
     *
     * @param limit how many to return (1 to 50)
     */
    List<String> getTopReviewedCoverImages(int limit);

    /**
     * The same books as {@link #getTopReviewedCoverImages}, in the same order, with their title, description and the
     * audio that introduces them when they have one.
     *
     * @param limit how many to return (1 to 50)
     */
    List<BookCoverResponse> getTopReviewedBooks(int limit);

    /**
     * Cover image URLs only, of the published books, newest first.
     *
     * @param page zero-based page index
     * @param size number of books per page (1 to 200)
     */
    PageResponse<String> getCoverImages(int page, int size);

    /**
     * Returns the user-registration roles available for public signup
     * (Author and Reader only).
     *
     * @return list of {@link RoleMetadataDto}
     */
    List<RoleMetadataDto> getRoles();
}
