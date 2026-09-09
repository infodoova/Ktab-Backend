package com.doova.ktab.repository.book;

import com.doova.ktab.model.book.BookPage;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Stream;

@Repository
public interface BookPageRepository extends JpaRepository<BookPage, Long> {

    boolean existsByBook_IdAndPageNumber(Long bookId, int pageNumber);

    boolean existsByBookIdAndPageNumber(Long bookId, int pageNumber);

    List<BookPage> findByBookIdOrderByPageNumberAsc(Long bookId);

    List<BookPage> findByBookIdAndPageNumberBetweenOrderByPageNumberAsc(Long bookId, int from, int to);

    @Query("""
                select cast(coalesce(sum(bs.wordCount), 0) as integer)
                from BookPage bs
                where bs.book.id = :bookId
            """)
    int getTotalWordCount(@Param("bookId") Long bookId);

    @Query("""
                SELECT s
                FROM BookPage s
                WHERE s.book.id = :bookId
                ORDER BY s.pageNumber ASC
            """)
    @QueryHints({@QueryHint(name = HibernateHints.HINT_FETCH_SIZE, value = "10"), @QueryHint(name = HibernateHints.HINT_READ_ONLY, value = "true")})
    Stream<BookPage> streamByBookIdOrderByPageNumberAsc(@Param("bookId") Long bookId);
}