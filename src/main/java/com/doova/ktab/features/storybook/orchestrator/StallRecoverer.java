package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.illustration.IllustrationPersistence;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Restarts drawing for books that have stalled: every page of a book in ILLUSTRATING should have an image or a job on its
 * way to making one. A job can end "successfully" without its result landing (a restart mid-step, a state change while it
 * ran), and nothing else would notice. This only looks at books that are drawing, and only restarts work whose job
 * finished more than the grace period ago, so it never races a step that is still in flight.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
public class StallRecoverer {

    private final StorybookRepository books;
    private final IllustrationPersistence illustration;

    @Value("${ktab.storybook.stall-recovery.grace:3m}")
    private Duration grace = Duration.ofMinutes(3);

    @Scheduled(fixedDelayString = "${ktab.storybook.stall-recovery.every:5m}", initialDelayString = "${ktab.storybook.stall-recovery.every:5m}")
    public void recover() {
        Instant succeededBefore = Instant.now().minus(grace);
        for (Long bookId : books.findIdsByStatus(StorybookStatus.ILLUSTRATING)) {
            try {
                illustration.reconcile(bookId, succeededBefore);
            } catch (RuntimeException e) {
                // One book's trouble must not stop the others from being checked.
                log.warn("storybook {}: stall check failed: {}", bookId, e.toString());
            }
        }
    }
}
