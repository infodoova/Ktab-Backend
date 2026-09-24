package com.doova.ktab.features.studio.repository;

import com.doova.ktab.features.studio.model.BookAudioChapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookAudioChapterRepository extends JpaRepository<BookAudioChapter, Long> {

    List<BookAudioChapter> findByBook_IdOrderBySortOrderAsc(Long bookId);

    Optional<BookAudioChapter> findByBook_IdAndSortOrder(Long bookId, Integer sortOrder);

    void deleteByBook_Id(Long bookId);
}
