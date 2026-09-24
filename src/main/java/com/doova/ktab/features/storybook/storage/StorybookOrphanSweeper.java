package com.doova.ktab.features.storybook.storage;

import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/** Removes R2 objects of books whose rows were deleted by any path (profile or account deletion). */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
public class StorybookOrphanSweeper {

    private final StorybookAssetStore store;
    private final StorybookRepository books;

    @Scheduled(cron = "${ktab.storybook.orphan-sweep.cron:0 30 3 * * *}")
    public void sweep() {
        Set<Long> inStorage = store.listBookIds();
        if (inStorage.isEmpty()) {
            return;
        }
        Set<Long> existing = books.findAllById(inStorage).stream().map(Storybook::getId).collect(Collectors.toSet());
        for (Long id : inStorage) {
            if (!existing.contains(id)) {
                log.info("storybook orphan sweep: deleting R2 assets of deleted book {}", id);
                store.deletePrefix("storybook/" + id + "/");
            }
        }
    }
}
