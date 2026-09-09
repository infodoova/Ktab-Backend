package com.doova.ktab.service.book;

import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.utils.pagination.PageResponse;

public interface BookSimilarityService {

    PageResponse<BookResponseDto> getSmartSimilarBooks(Long bookId, int page, int size);
}