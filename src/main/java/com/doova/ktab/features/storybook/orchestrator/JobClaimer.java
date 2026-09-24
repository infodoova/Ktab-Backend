package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JobClaimer {

    private final StorybookJobRepository jobs;
    private final EntityManager entityManager;

    /**
     * The SELECT ... FOR UPDATE SKIP LOCKED and the UPDATE run in one transaction, so the row
     * locks are held until the RUNNING state is committed; a concurrent claimer skips them.
     */
    @Transactional
    public List<StorybookJob> claim(String workerId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Long> ids = jobs.lockDueJobIds(limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        jobs.markRunning(ids, workerId);
        entityManager.flush();
        entityManager.clear(); // re-read the rows as updated by the native query
        return jobs.findAllById(ids);
    }

    @Transactional
    public int releaseStale(Duration lease) {
        return jobs.releaseStaleRunning(Instant.now().minus(lease));
    }
}
