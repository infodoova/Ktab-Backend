package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorybookPageImageRepository extends JpaRepository<StorybookPageImage, Long> {
    Optional<StorybookPageImage> findByPage_IdAndGeneration(Long pageId, int generation);
    List<StorybookPageImage> findByStatusOrderByCreatedAtAsc(PageImageStatus status);

    @org.springframework.data.jpa.repository.Query("""
            select i from StorybookPageImage i join fetch i.page p join fetch p.storybook
            where i.status = com.doova.ktab.features.storybook.enums.PageImageStatus.FLAGGED
              and p.currentImage = i
            order by i.createdAt asc
            """)
    List<StorybookPageImage> findFlaggedCurrentImages();

    @org.springframework.data.jpa.repository.Query("""
            select i from StorybookPageImage i join fetch i.page p join fetch p.storybook b
            where i.status = com.doova.ktab.features.storybook.enums.PageImageStatus.FLAGGED
              and p.currentImage = i
              and b.id = :bookId
            order by p.pageIndex asc
            """)
    List<StorybookPageImage> findFlaggedCurrentImagesByStorybookId(@org.springframework.data.repository.query.Param("bookId") Long bookId);
}
