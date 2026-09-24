package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorybookPageImageRepository extends JpaRepository<StorybookPageImage, Long> {
    Optional<StorybookPageImage> findByPage_IdAndGeneration(Long pageId, int generation);
    List<StorybookPageImage> findByStatusOrderByCreatedAtAsc(PageImageStatus status);
}
