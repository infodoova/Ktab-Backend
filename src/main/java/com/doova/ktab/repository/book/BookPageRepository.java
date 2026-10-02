package com.doova.ktab.repository.book;

import com.doova.ktab.model.book.BookPage;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface BookPageRepository extends JpaRepository<BookPage, Long>, JpaSpecificationExecutor<BookPage> {

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

    @Modifying
    @Query(value = """
            UPDATE tbl_book_pages
            SET col_word_count = CASE
                WHEN trim(coalesce(col_markdown_clean, col_markdown_content)) = '' THEN 0
                ELSE array_length(regexp_split_to_array(trim(coalesce(col_markdown_clean, col_markdown_content)), E'\\\\s+'), 1)
            END
            WHERE col_book_id = :bookId AND (col_word_count IS NULL OR col_word_count = 0)
            """, nativeQuery = true)
    int backfillWordCountsIfMissing(@Param("bookId") Long bookId);

    @Query("""
                select cast(coalesce(sum(length(coalesce(bp.markdownClean, bp.markdownContent))), 0) as long)
                from BookPage bp
                where bp.book.id = :bookId
            """)
    long getTotalCharacterCount(@Param("bookId") Long bookId);

    java.util.Optional<BookPage> findByBookIdAndPageNumber(Long bookId, int pageNumber);

    List<BookPage> findByBookIdAndStatusInOrderByPageNumberAsc(Long bookId, java.util.Collection<com.doova.ktab.enums.status.OcrStatus> statuses);

    List<BookPage> findByBookIdAndPageKindOrderByPageNumberAsc(Long bookId, com.doova.ktab.enums.book.PageKind pageKind);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE BookPage p SET p.pageNumber = p.pageNumber + :delta WHERE p.book.id = :bookId AND p.pageNumber >= :fromPage")
    void shiftPageNumbers(@Param("bookId") Long bookId, @Param("fromPage") int fromPage, @Param("delta") int delta);

    @Modifying
    @Query("DELETE FROM BookPage p WHERE p.book.id = :bookId")
    void deleteByBook_Id(@Param("bookId") Long bookId);

    @Query(value = """
            SELECT * FROM tbl_book_pages
            WHERE col_book_id = :bookId
              AND coalesce(col_markdown_clean, col_markdown_content) IS NOT NULL
              AND to_tsvector('simple', coalesce(col_markdown_clean, col_markdown_content)) @@ plainto_tsquery('simple', :query)
            ORDER BY ts_rank(to_tsvector('simple', coalesce(col_markdown_clean, col_markdown_content)), plainto_tsquery('simple', :query)) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<BookPage> searchPagesFullText(@Param("bookId") Long bookId, @Param("query") String query, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM tbl_book_pages
            WHERE col_book_id = :bookId
              AND coalesce(col_markdown_clean, col_markdown_content) IS NOT NULL
              AND length(trim(coalesce(col_markdown_clean, col_markdown_content))) > 30
            ORDER BY col_page_number ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<BookPage> findFirstPagesWithContent(@Param("bookId") Long bookId, @Param("limit") int limit);

    @Query("""
                SELECT s
                FROM BookPage s
                WHERE s.book.id = :bookId
                ORDER BY s.pageNumber ASC
            """)
    @QueryHints({@QueryHint(name = HibernateHints.HINT_FETCH_SIZE, value = "10"), @QueryHint(name = HibernateHints.HINT_READ_ONLY, value = "true")})
    Stream<BookPage> streamByBookIdOrderByPageNumberAsc(@Param("bookId") Long bookId);

    /**
     * Idempotent upsert lookup for Studio synthetic pages — keyed on external chapter id
     * and chapter-local ordinal. Re-projecting the same chapter finds existing page rows.
     * See docs/ocr_engine_v3.md, Phase 3.3 and StudioProjectorService.
     */
    Optional<BookPage> findByBook_IdAndExternalChapterIdAndChapterPageOrdinal(
            Long bookId, String externalChapterId, Integer chapterPageOrdinal);

    List<BookPage> findByBook_IdAndExternalChapterIdOrderByChapterPageOrdinalAsc(
            Long bookId, String externalChapterId);

    /**
     * Delete all synthetic pages for a chapter before rebuilding on a content-hash mismatch.
     * Called by {@link com.doova.ktab.features.studio.sync.StudioProjectorService#reproject}.
     */
    @Modifying
    @Query("DELETE FROM BookPage p WHERE p.book.id = :bookId AND p.externalChapterId = :externalChapterId")
    void deleteByBook_IdAndExternalChapterId(
            @Param("bookId") Long bookId,
            @Param("externalChapterId") String externalChapterId);
}