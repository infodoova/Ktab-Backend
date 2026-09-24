package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.StorybookPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorybookPageRepository extends JpaRepository<StorybookPage, Long> {
    List<StorybookPage> findByStorybook_IdOrderByPageIndexAsc(Long storybookId);
    Optional<StorybookPage> findByStorybook_IdAndPageIndex(Long storybookId, int pageIndex);
    void deleteByStorybook_Id(Long storybookId);
}
