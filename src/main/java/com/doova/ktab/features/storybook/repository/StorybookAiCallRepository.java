package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.StorybookAiCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StorybookAiCallRepository extends JpaRepository<StorybookAiCall, Long> {
    List<StorybookAiCall> findByStorybookIdOrderByIdAsc(Long storybookId);
}
