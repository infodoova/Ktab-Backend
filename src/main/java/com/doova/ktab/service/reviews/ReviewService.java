package com.doova.ktab.service.reviews;


import com.doova.ktab.dto.request.ReviewRequestDto;
import com.doova.ktab.dto.response.IsReviewedResponseDto;
import com.doova.ktab.dto.response.ReviewResponseDto;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookReview;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.review.ReviewRepository;
import com.doova.ktab.repository.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------
    // CREATE REVIEW
    // ------------------------------------------------------------
    @Transactional
    public void createReview(Long bookId, ReviewRequestDto req, User reader) {

        Book book = bookRepository.findByIdForUpdate(bookId).orElseThrow(() -> new EntityNotFoundException(ApiMessageKey.REVIEW_BOOK_NOT_FOUND.getKey()));

        if (reviewRepository.existsByReaderIdAndBookId(reader.getId(), bookId)) {
            throw new IllegalStateException(ApiMessageKey.REVIEW_ALREADY_EXISTS.getKey());
        }

        BookReview review = new BookReview();
        review.setBook(book);
        review.setReader(reader);
        review.setRating(req.rating());
        review.setComment(req.comment());

        book.addReview(review);
        updateBookRating(book);
    }


    // ------------------------------------------------------------
    // GET REVIEWS FOR A BOOK
    // ------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getReviewsByBook(Long bookId) {

        List<BookReview> reviews = reviewRepository.findAllByBookIdOrderByAuditCreatedAtDesc(bookId);

        return reviews.stream().map(r -> new ReviewResponseDto(r.getId(), r.getRating(), r.getComment(), r.getReader().getId(), r.getReader().getFullName(), LocalDateTime.ofInstant(r.getAudit().getCreatedAt(), ZoneOffset.UTC))).toList();
    }

    // Define the complex mapping as a private method or a static final Function
    private final Function<BookReview, ReviewResponseDto> toDto = r -> new ReviewResponseDto(r.getId(), r.getRating(), r.getComment(), r.getReader().getId(), r.getReader().getFullName(), LocalDateTime.ofInstant(r.getAudit().getCreatedAt(), ZoneOffset.UTC));

    @Transactional(readOnly = true)
    public Page<ReviewResponseDto> getReviewsByBookPrioritizeUser(Long bookId, Long userId, Pageable pageable) {

        // ─────────────────────────────────────────────
        // NOT LOGGED IN → normal pagination
        // ─────────────────────────────────────────────
        if (userId == null) {
            return reviewRepository.findAllByBookIdOrderByAuditCreatedAtDesc(bookId, pageable).map(toDto);
        }

        // ─────────────────────────────────────────────
        // USER REVIEW
        // ─────────────────────────────────────────────
        Optional<BookReview> userReviewOpt = reviewRepository.findByReaderIdAndBookId(userId, bookId);

        // No user review → normal pagination
        if (userReviewOpt.isEmpty()) {
            return reviewRepository.findAllByBookIdOrderByAuditCreatedAtDesc(bookId, pageable).map(toDto);
        }

        // ─────────────────────────────────────────────
        // PAGE 0 → user review + remaining slots
        // ─────────────────────────────────────────────
        if (pageable.getPageNumber() == 0) {

            int remainingSize = pageable.getPageSize() - 1;

            Page<BookReview> othersPage = reviewRepository.findAllByBookIdAndReaderIdNotOrderByAuditCreatedAtDesc(bookId, userId, PageRequest.of(0, Math.max(remainingSize, 0)));

            List<BookReview> combined = new ArrayList<>();
            combined.add(userReviewOpt.get());
            combined.addAll(othersPage.getContent());

            return new PageImpl<>(combined.stream().map(toDto).toList(), pageable, othersPage.getTotalElements() + 1);
        }

        // ─────────────────────────────────────────────
        // PAGE > 0 → skip user review
        // ─────────────────────────────────────────────
        Pageable shiftedPageable = PageRequest.of(pageable.getPageNumber() - 1, pageable.getPageSize());

        return reviewRepository.findAllByBookIdAndReaderIdNotOrderByAuditCreatedAtDesc(bookId, userId, shiftedPageable).map(toDto);
    }


    // ------------------------------------------------------------
    // DELETE REVIEW
    // ------------------------------------------------------------
    @Transactional
    public void deleteReview(Long reviewId, Long bookId, Long readerId) {

        BookReview review = reviewRepository.findByReaderIdAndBookIdAndId(readerId, bookId, reviewId).orElseThrow(() -> new EntityNotFoundException(ApiMessageKey.REVIEW_NOT_FOUND.getKey()));

        Book book = review.getBook();
        book.removeReview(review);
        updateBookRating(book);
    }


    // ------------------------------------------------------------
    // UPDATE BOOK AVERAGE RATING
    // ------------------------------------------------------------
    private void updateBookRating(Book book) {

        List<BookReview> reviews = reviewRepository.findAllByBookIdOrderByAuditCreatedAtDesc(book.getId());

        if (reviews.isEmpty()) {
            book.setAverageRating(BigDecimal.ZERO);
            book.setTotalReviews(0);
        } else {
            int sum = reviews.stream().mapToInt(BookReview::getRating).sum();
            int count = reviews.size();

            BigDecimal average = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

            book.setAverageRating(average);
            book.setTotalReviews(count);
        }

        bookRepository.save(book);
    }

    @Transactional(readOnly = true)
    public IsReviewedResponseDto isReviewed(Long bookId, Long userId) {

        Optional<BookReview> reviewOpt = reviewRepository.findByReaderIdAndBookId(userId, bookId);

        if (reviewOpt.isEmpty()) {
            return new IsReviewedResponseDto(false, null, null, null);
        }

        BookReview r = reviewOpt.get();
        return new IsReviewedResponseDto(true, r.getRating(), r.getComment(), r.getId());
    }

    @Transactional
    public void updateReview(Long bookId, ReviewRequestDto req, User reader, Long reviewId) {

        Book book = bookRepository.findByIdForUpdate(bookId).orElseThrow(() -> new EntityNotFoundException(ApiMessageKey.REVIEW_BOOK_NOT_FOUND.getKey()));

        BookReview review = reviewRepository.findByReaderIdAndBookIdAndId(reader.getId(), bookId, reviewId).orElseThrow(() -> new EntityNotFoundException(ApiMessageKey.REVIEW_NOT_FOUND.getKey()));

        review.setRating(req.rating());
        review.setComment(req.comment());

        updateBookRating(book);
    }


}
