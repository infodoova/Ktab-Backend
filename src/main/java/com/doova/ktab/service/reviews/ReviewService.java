package com.doova.ktab.service.reviews;

import com.doova.ktab.dto.review.IsReviewedResponseDto;
import com.doova.ktab.dto.review.ReviewRequestDto;
import com.doova.ktab.dto.review.ReviewResponseDto;
import com.doova.ktab.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReviewService {

    void createReview(Long bookId, ReviewRequestDto req, User reader);

    List<ReviewResponseDto> getReviewsByBook(Long bookId);

    Page<ReviewResponseDto> getReviewsByBookPrioritizeUser(Long bookId, Long userId, Pageable pageable);

    void deleteReview(Long reviewId, Long bookId, Long readerId);

    IsReviewedResponseDto isReviewed(Long bookId, Long userId);

    void updateReview(Long bookId, ReviewRequestDto req, User reader, Long reviewId);

    Page<ReviewResponseDto> searchReviews(Long bookId, com.doova.ktab.dto.review.ReviewSearchRequest req, Pageable pageable);
}
