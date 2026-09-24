package com.doova.ktab.repository.book;

import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.model.book.BookSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookSectionRepository extends JpaRepository<BookSection, Long> {

    List<BookSection> findByBook_IdOrderBySortOrderAsc(Long bookId);

    List<BookSection> findByBook_IdAndParentIsNullOrderBySortOrderAsc(Long bookId);

    List<BookSection> findByBook_IdAndSource(Long bookId, StructureSource source);

    /**
     * Upsert lookup for Studio projection — keyed on the stable external chapter id so that
     * re-projecting the same chapter finds the existing section row.
     * See docs/ocr_engine_v3.md, Phase 3.3 and StudioProjectorService.
     */
    Optional<BookSection> findByBook_IdAndExternalChapterId(Long bookId, String externalChapterId);

    @Modifying
    @Query("DELETE FROM BookSection s WHERE s.book.id = :bookId AND s.source <> :source")
    void deleteByBook_IdAndSourceNot(@Param("bookId") Long bookId, @Param("source") StructureSource source);

    @Modifying
    @Query("DELETE FROM BookSection s WHERE s.book.id = :bookId")
    void deleteByBook_Id(@Param("bookId") Long bookId);
}
