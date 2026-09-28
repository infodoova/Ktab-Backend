package com.doova.ktab.features.talktobook.repository;

import com.doova.ktab.features.talktobook.model.BookAgentRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookAgentRecordRepository extends JpaRepository<BookAgentRecord, UUID> {

    /** Content digest also detects deletes and bulk updates that bypass entity auditing. */
    @Query(value = """
        SELECT md5(
            coalesce((SELECT string_agg(md5(CAST(jsonb_build_array(
                p.col_page_number, coalesce(p.col_markdown_clean, p.col_markdown_content)
            ) AS text)), '' ORDER BY p.col_id)
            FROM tbl_book_pages p WHERE p.col_book_id = :bookId), '') || ':' ||
            coalesce((SELECT string_agg(md5(CAST(jsonb_build_array(
                s.col_title, s.col_start_page, s.col_end_page, s.col_sort_order
            ) AS text)), '' ORDER BY s.col_id)
            FROM tbl_book_sections s WHERE s.col_book_id = :bookId), '')
        )
        """, nativeQuery = true)
    String computeContentRevision(@Param("bookId") Long bookId);

    /**
     * Exact match lookup by book and normalized SHA-256 question hash.
     */
    Optional<BookAgentRecord> findByBookIdAndQuestionHash(Long bookId, String questionHash);

    /**
     * Retrieves all cached records for a book to perform semantic cosine similarity matching.
     */
    List<BookAgentRecord> findByBookId(Long bookId);

    /**
     * Counts the total number of cached records for a specific book.
     */
    long countByBookId(Long bookId);

    /**
     * Finds UUIDs of the least frequently used and oldest accessed records for a book.
     */
    @Query("""
        SELECT r.id FROM BookAgentRecord r
        WHERE r.book.id = :bookId
        ORDER BY r.countUsed ASC, r.lastAccessedAt ASC
    """)
    List<UUID> findIdsForEviction(@Param("bookId") Long bookId, Pageable pageable);

    /**
     * Deletes records in batch by UUID.
     */
    @Modifying
    @Query("DELETE FROM BookAgentRecord r WHERE r.id IN :ids")
    void deleteAllByIdIn(@Param("ids") List<UUID> ids);
}
