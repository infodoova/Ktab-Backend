package com.doova.ktab.features.imagegen.repository;

import com.doova.ktab.features.imagegen.enums.ImageGenerationStatus;
import com.doova.ktab.features.imagegen.model.GeneratedImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeneratedImageRepository extends JpaRepository<GeneratedImage, UUID>, JpaSpecificationExecutor<GeneratedImage> {

    Optional<GeneratedImage> findByIdAndUserId(UUID id, Long userId);

    @Query("SELECT g FROM GeneratedImage g WHERE g.book.id = :bookId AND g.user.id = :userId ORDER BY g.createdAt DESC")
    Page<GeneratedImage> findAllByBookIdAndUserId(@Param("bookId") Long bookId, @Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(g) FROM GeneratedImage g WHERE g.user.id = :userId AND g.status IN :statuses")
    long countByUserIdAndStatusIn(@Param("userId") Long userId, @Param("statuses") Collection<ImageGenerationStatus> statuses);

    @Query("SELECT COUNT(g) FROM GeneratedImage g WHERE g.user.id = :userId AND g.createdAt >= :after")
    long countByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("after") LocalDateTime after);

    @Query("SELECT COUNT(g) FROM GeneratedImage g WHERE g.user.id = :userId AND g.createdAt >= :after AND g.status != :excludedStatus")
    long countByUserIdAndCreatedAtAfterAndStatusNot(
            @Param("userId") Long userId,
            @Param("after") LocalDateTime after,
            @Param("excludedStatus") ImageGenerationStatus excludedStatus
    );

    @Query("SELECT COUNT(g) > 0 FROM GeneratedImage g WHERE g.user.id = :userId AND g.book.id = :bookId AND g.promptHash = :promptHash AND g.status IN :statuses")
    boolean existsInFlightDuplicate(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId,
            @Param("promptHash") String promptHash,
            @Param("statuses") Collection<ImageGenerationStatus> statuses
    );

    @Query("SELECT g FROM GeneratedImage g JOIN FETCH g.book b WHERE g.user.id = :userId AND (:bookId IS NULL OR b.id = :bookId) ORDER BY g.createdAt DESC")
    Page<GeneratedImage> findAllByUserIdAndOptionalBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId,
            Pageable pageable
    );

    @Query("SELECT new com.doova.ktab.features.imagegen.dto.response.ReaderBookImageSummary(b.id, b.title, COUNT(g)) " +
            "FROM GeneratedImage g JOIN g.book b " +
            "WHERE g.user.id = :userId " +
            "GROUP BY b.id, b.title " +
            "ORDER BY MAX(g.createdAt) DESC")
    java.util.List<com.doova.ktab.features.imagegen.dto.response.ReaderBookImageSummary> findDistinctBooksWithImageCounts(
            @Param("userId") Long userId
    );

    @Query("SELECT g FROM GeneratedImage g JOIN FETCH g.book b WHERE g.user.id = :userId ORDER BY b.title ASC, g.createdAt DESC")
    java.util.List<GeneratedImage> findAllByUserIdWithBook(@Param("userId") Long userId);
}
