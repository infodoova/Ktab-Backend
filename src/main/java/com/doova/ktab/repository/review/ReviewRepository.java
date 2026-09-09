package com.doova.ktab.repository.review;

import com.doova.ktab.model.book.BookReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<BookReview, Long> {

    @EntityGraph(attributePaths = {"reader"})
    List<BookReview> findAllByBookIdOrderByCreatedAtDesc(Long bookId);

    boolean existsByReaderIdAndBookId(Long readerId, Long bookId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM BookReview r WHERE r.reader.id = :readerId")
    void deleteAllByReaderId(@Param("readerId") Long readerId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM BookReview r WHERE r.book.id = :bookId")
    void deleteAllByBookId(@Param("bookId") Long bookId);

    Optional<BookReview> findByReaderIdAndBookIdAndId(Long readerId, Long bookId, Long reviewId);

    Optional<BookReview> findByReaderIdAndBookId(Long readerId, Long bookId);

    // Get all other reviews, ordered by creation date, excluding the user
    @EntityGraph(attributePaths = {"reader"})
    List<BookReview> findAllByBookIdAndReaderIdNotOrderByCreatedAtDesc(Long bookId, Long userId);

    @EntityGraph(attributePaths = {"reader"})
    Page<BookReview> findAllByBookIdOrderByCreatedAtDesc(Long bookId, Pageable pageable);

    @EntityGraph(attributePaths = {"reader"})
    Page<BookReview> findAllByBookIdAndReaderIdNotOrderByCreatedAtDesc(Long bookId, Long readerId, Pageable pageable);
}