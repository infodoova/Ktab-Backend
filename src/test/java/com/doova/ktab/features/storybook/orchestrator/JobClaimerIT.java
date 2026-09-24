package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.JobStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import({JobClaimer.class, JobEnqueuer.class})
class JobClaimerIT extends StorybookJpaIT {

    @Autowired JobClaimer claimer;
    @Autowired JobEnqueuer enqueuer;
    @Autowired StorybookJobRepository jobs;

    @Test
    void claimsDueJobsOldestFirstAndMarksThemRunning() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "claim@example.com"));
        enqueuer.enqueue(book.getId(), JobStep.ILLUSTRATE_PAGE, 1, 1);
        enqueuer.enqueue(book.getId(), JobStep.ILLUSTRATE_PAGE, 2, 1);
        enqueuer.enqueue(book.getId(), JobStep.ILLUSTRATE_PAGE, 3, 1);
        em.clear();

        List<StorybookJob> claimed = claimer.claim("worker-a", 2);

        assertThat(claimed).hasSize(2).allSatisfy(j -> {
            assertThat(j.getStatus()).isEqualTo(JobStatus.RUNNING);
            assertThat(j.getLockedBy()).isEqualTo("worker-a");
            assertThat(j.getAttempts()).isEqualTo(1);
        });
        assertThat(claimer.claim("worker-a", 10)).hasSize(1);
        assertThat(claimer.claim("worker-a", 10)).isEmpty();
    }

    @Test
    void futureJobsAreNotClaimed() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "future@example.com"));
        enqueuer.enqueue(book.getId(), JobStep.STORY_PLAN, -1, 0);
        StorybookJob job = jobs.findByStorybookIdOrderByIdAsc(book.getId()).get(0);
        job.setNextRunAt(Instant.now().plus(Duration.ofHours(1)));
        em.flush();
        em.clear();

        assertThat(claimer.claim("w", 10)).isEmpty();
    }

    @Test
    void staleRunningJobsAreReleased() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "stale@example.com"));
        enqueuer.enqueue(book.getId(), JobStep.STORY_PLAN, -1, 0);
        em.clear();
        StorybookJob job = claimer.claim("dead-worker", 1).get(0);
        em.getEntityManager().createNativeQuery(
                "update tbl_storybook_jobs set col_locked_at = now() - interval '20 minutes' where col_id = :id")
                .setParameter("id", job.getId()).executeUpdate();
        em.clear();

        assertThat(claimer.releaseStale(Duration.ofMinutes(10))).isEqualTo(1);
        StorybookJob released = jobs.findById(job.getId()).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(released.getLockedBy()).isNull();
        assertThat(released.getAttempts()).isEqualTo(1);
    }
}
