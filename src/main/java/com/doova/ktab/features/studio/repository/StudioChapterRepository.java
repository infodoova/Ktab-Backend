package com.doova.ktab.features.studio.repository;

import com.doova.ktab.features.studio.model.StudioChapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudioChapterRepository extends JpaRepository<StudioChapter, Long> {

    List<StudioChapter> findByProject_IdOrderByOrderIndexAsc(Long projectId);

    Optional<StudioChapter> findByProject_IdAndExternalChapterId(@Param("projectId") Long projectId,
                                                                  @Param("externalChapterId") String externalChapterId);

    Optional<StudioChapter> findByBookSection_Id(@Param("bookSectionId") Long bookSectionId);
}
