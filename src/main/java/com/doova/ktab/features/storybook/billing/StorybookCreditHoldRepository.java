package com.doova.ktab.features.storybook.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorybookCreditHoldRepository extends JpaRepository<StorybookCreditHold, Long> {
    Optional<StorybookCreditHold> findByStorybookId(Long storybookId);
}
