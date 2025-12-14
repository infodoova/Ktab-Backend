package com.doova.ktab.repository.book;

import com.doova.ktab.enums.BookStatus;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("""
    SELECT b FROM Book b
    WHERE b.id <> :bookId
      AND (b.mainGenre.id = :mainGenreId)
      AND (
            b.ageRangeMin <= :ageMax 
        AND b.ageRangeMax >= :ageMin
      )
""")
    List<Book> findBroadCandidates(
            @Param("bookId") Long bookId,
            @Param("mainGenreId") Long mainGenreId,
            @Param("ageMin") Integer ageMin,
            @Param("ageMax") Integer ageMax
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);
}