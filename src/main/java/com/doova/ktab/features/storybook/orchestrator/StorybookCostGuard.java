package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StorybookCostGuard {

    private final StorybookRepository books;
    private final StorybookProperties properties;

    @Transactional(readOnly = true)
    public boolean exceeded(Long bookId) {
        return books.findById(bookId)
                .map(b -> b.getTotalCostUsd().compareTo(properties.getLimits().getMaxBookCostUsd()) > 0)
                .orElse(false);
    }
}
