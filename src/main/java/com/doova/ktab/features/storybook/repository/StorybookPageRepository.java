package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.StorybookPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StorybookPageRepository extends JpaRepository<StorybookPage, Long> {
    List<StorybookPage> findByStorybook_IdOrderByPageIndexAsc(Long storybookId);
    Optional<StorybookPage> findByStorybook_IdAndPageIndex(Long storybookId, int pageIndex);
    void deleteByStorybook_Id(Long storybookId);

    @Query("SELECT p FROM StorybookPage p LEFT JOIN FETCH p.currentImage WHERE p.storybook.id IN :storybookIds AND p.pageIndex = 0")
    List<StorybookPage> findCoversByStorybookIds(@Param("storybookIds") Collection<Long> storybookIds);

    @Query("SELECT p FROM StorybookPage p LEFT JOIN FETCH p.currentImage WHERE p.storybook.id = :storybookId AND p.pageIndex = 0")
    Optional<StorybookPage> findCoverByStorybookId(@Param("storybookId") Long storybookId);
}
