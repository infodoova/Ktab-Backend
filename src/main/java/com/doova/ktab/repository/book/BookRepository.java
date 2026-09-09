package com.doova.ktab.repository.book;

import com.doova.ktab.dto.analytics.AuthorBookAnalyticsResponse;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing Book entities.
 * Extends JpaRepository to inherit basic CRUD operations.
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Finds all books written by a specific author (User).
     *
     * @param authorId The ID of the author (User).
     * @return A list of books by that author.
     */
    List<Book> findAllByAuthorId(Long authorId);

    /**
     * Finds a book by its author ID and title (to respect the unique constraint).
     *
     * @param authorId The ID of the author.
     * @param title    The title of the book.
     * @return An Optional containing the Book if found.
     */
    Optional<Book> findByAuthorIdAndTitle(Long authorId, String title);

    Optional<Book> findByIdAndAuthor(Long bookId, User author);


    Page<Book> findAllByAuthorId(Long authorId, Pageable pageable);

    Page<Book> findAllByAuthorIdAndStatus(Long authorId, BookStatus status, Pageable pageable);

    Page<Book> findAllByLibraryOrganizationId(Long libraryOrgId, Pageable pageable);

    Page<Book> findAllByLibraryOrganizationIdAndStatus(Long libraryOrgId, BookStatus status, Pageable pageable);

    Optional<Book> findByIdAndLibraryOrganizationId(Long bookId, Long libraryOrgId);

    long countByLibraryOrganizationId(Long libraryOrgId);

    @Query("""
                SELECT b FROM Book b
                WHERE b.id <> :bookId
                  AND (b.mainGenre.id = :mainGenreId)
                  AND (
                        b.ageRangeMin <= :ageMax 
                    AND b.ageRangeMax >= :ageMin
                  )
            """)
    List<Book> findBroadCandidates(@Param("bookId") Long bookId, @Param("mainGenreId") Long mainGenreId, @Param("ageMin") Integer ageMin, @Param("ageMax") Integer ageMax);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);

    long countByAuthor_Id(Long authorId);

    @Query("""
                select coalesce(avg(b.averageRating), 0)
                from Book b
                where b.author.id = :authorId
                  and b.totalReviews > 0
            """)
    BigDecimal findAuthorAverageRating(Long authorId);

    @Query("""
                select coalesce(sum(b.totalReviews), 0)
                from Book b
                where b.author.id = :authorId
            """)
    long sumAuthorTotalReviews(Long authorId);

    @Query("""
                select new com.doova.ktab.dto.analytics.AuthorBookAnalyticsResponse(
                    b.id,
                    b.title,
                    b.status,
                    case\s
                        when b.status = com.doova.ktab.enums.status.BookStatus.PUBLISHED\s
                        then b.publishDate\s
                        else null\s
                    end,
                    b.averageRating,
                    b.totalReviews,
                    b.mainGenre.nameAr,
                    count(distinct ble.id),
                    max(a.storagePath)
                )
                from Book b
                left join BookLibraryEntry ble on ble.book.id = b.id
                left join Attachment a
                    on a.entityId = b.id
                   and a.entityType = 'Book'
                   and a.type = 'COVER_IMAGE'
                where b.author.id = :authorId
                group by
                    b.id,
                    b.title,
                    b.status,
                    b.publishDate,
                    b.averageRating,
                    b.totalReviews,
                    b.mainGenre.nameAr
           \s""")
    Page<AuthorBookAnalyticsResponse> findAuthorBooksWithAnalytics(Long authorId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Book b SET b.ocrStatus = :status WHERE b.id = :id")
    void updateOcrStatus(Long id, OcrStatus status);

}