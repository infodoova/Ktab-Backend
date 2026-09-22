package com.doova.ktab.service.publisher;

import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.book.BookSourceFileResponseDto;
import com.doova.ktab.dto.book.PublisherReviewSearchRequest;
import com.doova.ktab.dto.publisher.ReviewDecisionRequest;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.pagination.PageResponse;

public interface PublisherReviewService {

    PageResponse<BookResponseDto> getReviewQueue(int page, int size);

    PageResponse<BookResponseDto> searchReviewQueue(PublisherReviewSearchRequest req);

    BookResponseDto getBookById(Long id);

    BookSourceFileResponseDto getSourceFileForPublisher(Long id, User publisher);

    BookResponseDto approveBook(Long id, ReviewDecisionRequest req, User publisher);

    BookResponseDto rejectBook(Long id, ReviewDecisionRequest req, User publisher);
}
