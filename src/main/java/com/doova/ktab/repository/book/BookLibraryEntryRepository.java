package com.doova.ktab.repository.book;

import com.doova.ktab.model.book.BookLibraryEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookLibraryEntryRepository extends JpaRepository<BookLibraryEntry, Long> {

    boolean existsByUserIdAndBookId(Long userId, Long bookId);

    @EntityGraph(attributePaths = {"book", "book.author", "book.mainGenre", "book.subGenre", "book.libraryOrganization"})
    List<BookLibraryEntry> findAllByUserId(Long userId);

    Optional<BookLibraryEntry> findByUserIdAndBookId(Long userId, Long bookId);

    @EntityGraph(attributePaths = {"book", "book.author", "book.mainGenre", "book.subGenre", "book.libraryOrganization"})
    Page<BookLibraryEntry> findAllByUserId(Long userId, Pageable pageable);

    @Query("""
        SELECT bl2.book.id, COUNT(bl2.user.id)
        FROM BookLibraryEntry bl1
        JOIN BookLibraryEntry bl2 ON bl1.user.id = bl2.user.id
        WHERE bl1.book.id = :bookId AND bl2.book.id <> :bookId
        GROUP BY bl2.book.id
    """)
    List<Object[]> findCollaborativeScores(@Param("bookId") Long bookId);

    @Query("""
        select count(e)
        from BookLibraryEntry e
        where e.book.author.id = :authorId
    """)
    long countAuthorTotalReads(@Param("authorId") Long authorId);

}
