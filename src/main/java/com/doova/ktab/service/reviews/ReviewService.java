package com.doova.ktab.service.reviews;


import com.doova.ktab.api.dto.request.ReviewRequestDto;
import com.doova.ktab.api.dto.response.IsReviewedResponseDto;
import com.doova.ktab.api.dto.response.ReviewResponseDto;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookReview;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.review.ReviewRepository;
import com.doova.ktab.repository.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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

        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));

        if (reviewRepository.existsByReaderIdAndBookId(reader.getId(), bookId)) {
            throw new IllegalStateException("You have already reviewed this book.");
        }

        BookReview review = new BookReview();
        review.setBook(book);
        review.setReader(reader);
        review.setRating(req.rating());
        review.setComment(req.comment());

        book.addReview(review);

        // ✅ keep as requested
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
    public List<ReviewResponseDto> getReviewsByBookPrioritizeUser(Long bookId, Long userId) {

        // --- SECTION 1: Handle Not Logged In (userId == null) ---
        if (userId == null) {
            return reviewRepository.findAllByBookIdOrderByAuditCreatedAtDesc(bookId).stream().map(toDto) // Use the centralized mapping
                    .toList();
        }

        // --- SECTION 2: Attempt to get User's Review (Query 1) ---
        Optional<BookReview> userReviewOpt = reviewRepository.findByReaderIdAndBookId(userId, bookId);

        // --- SECTION 3: Handle No User Review ---
        if (userReviewOpt.isEmpty()) {
            return reviewRepository.findAllByBookIdOrderByAuditCreatedAtDesc(bookId).stream().map(toDto) // Use the centralized mapping
                    .toList();
        }

        // --- SECTION 4: Build Priority List (Query 3) ---
        // Get all other reviews
        List<BookReview> otherReviews = reviewRepository.findAllByBookIdAndReaderIdNotOrderByAuditCreatedAtDesc(bookId, userId);

        // Build final ordered list
        List<BookReview> finalList = new ArrayList<>();
        finalList.add(userReviewOpt.get());
        finalList.addAll(otherReviews);

        return finalList.stream().map(toDto) // Use the centralized mapping
                .toList();
    }

    // ------------------------------------------------------------
    // DELETE REVIEW
    // ------------------------------------------------------------
    @Transactional
    public void deleteReview(Long reviewId, Long bookId, Long readerId) {

        BookReview review = reviewRepository.findByReaderIdAndBookIdAndId(readerId, bookId, reviewId).orElseThrow(() -> new EntityNotFoundException("Review not found"));

        Book book = review.getBook();

//        // Optional: restrict deleting to review owner
//        if (!review.getReader().getId().equals(userId)) {
//            throw new IllegalStateException("You cannot delete someone else's review.");
//        }

        // Remove it bidirectionally
        book.removeReview(review);

        // Update book rating
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

        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));

        BookReview review = reviewRepository
                .findByReaderIdAndBookIdAndId(reader.getId(), bookId, reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        review.setRating(req.rating());
        review.setComment(req.comment());

        updateBookRating(book);
    }

}
