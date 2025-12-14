package com.doova.ktab.repository.review;

import com.doova.ktab.model.book.BookReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<BookReview, Long> {

    List<BookReview> findAllByBookIdOrderByAuditCreatedAtDesc(Long bookId);

    boolean existsByReaderIdAndBookId(Long readerId, Long bookId);

    void deleteAllByReaderId(Long readerId);

    void deleteAllByBookId(Long bookId);

    Optional<BookReview> findByReaderIdAndBookIdAndId(Long readerId, Long bookId, Long reviewId);

    Optional<BookReview> findByReaderIdAndBookId(Long readerId, Long bookId);

    // 2. Get all other reviews, ordered by creation date, excluding the user
    List<BookReview> findAllByBookIdAndReaderIdNotOrderByAuditCreatedAtDesc(Long bookId, Long userId);

}