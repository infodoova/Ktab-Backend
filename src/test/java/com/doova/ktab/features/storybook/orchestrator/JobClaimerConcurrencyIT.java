package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Import({JobClaimer.class, JobEnqueuer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JobClaimerConcurrencyIT extends StorybookJpaIT {

    @Autowired JobClaimer claimer;
    @Autowired JobEnqueuer enqueuer;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void twoWorkersNeverClaimTheSameJob() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> {
            Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "race-" + System.nanoTime() + "@example.com"));
            for (int page = 1; page <= 40; page++) {
                enqueuer.enqueue(book.getId(), JobStep.ILLUSTRATE_PAGE, page, 1);
            }
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Set<Long>>> results = List.of(
                pool.submit(() -> drain(start, "worker-a")),
                pool.submit(() -> drain(start, "worker-b")));
        start.countDown();
        Set<Long> a = results.get(0).get();
        Set<Long> b = results.get(1).get();
        pool.shutdown();

        assertThat(a).doesNotContainAnyElementsOf(b);
        assertThat(a.size() + b.size()).isEqualTo(40);
    }

    private Set<Long> drain(CountDownLatch start, String worker) throws InterruptedException {
        start.await();
        Set<Long> ids = new HashSet<>();
        List<StorybookJob> batch;
        do {
            batch = claimer.claim(worker, 3);
            batch.forEach(j -> ids.add(j.getId()));
        } while (!batch.isEmpty());
        return ids;
    }
}
