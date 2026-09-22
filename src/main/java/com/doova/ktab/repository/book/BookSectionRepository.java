package com.doova.ktab.repository.book;

import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.model.book.BookSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookSectionRepository extends JpaRepository<BookSection, Long> {

    List<BookSection> findByBook_IdOrderBySortOrderAsc(Long bookId);

    List<BookSection> findByBook_IdAndParentIsNullOrderBySortOrderAsc(Long bookId);

    List<BookSection> findByBook_IdAndSource(Long bookId, StructureSource source);

    @Modifying
    @Query("DELETE FROM BookSection s WHERE s.book.id = :bookId AND s.source <> :source")
    void deleteByBook_IdAndSourceNot(@Param("bookId") Long bookId, @Param("source") StructureSource source);

    @Modifying
    @Query("DELETE FROM BookSection s WHERE s.book.id = :bookId")
    void deleteByBook_Id(@Param("bookId") Long bookId);
}
