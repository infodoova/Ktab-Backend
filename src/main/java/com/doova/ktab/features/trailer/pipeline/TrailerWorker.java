package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import java.util.function.Consumer;

/**
 * Single scheduler. Each trailer step is small (a few API calls), so one thread per tick is enough; @Version on
 * BookTrailer rejects a stale save if a user cancels mid-step or a second instance races (Review Focus 5).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
public class TrailerWorker {

    private final BookTrailerRepository trailers;
    private final TrailerLauncher launcher;
    private final TrailerReconciler reconciler;
    private final TrailerHarvester harvester;
    private final TrailerProperties properties;

    @Scheduled(fixedDelayString = "${ktab.trailer.worker-tick:15s}")
    public void tick() {
        long running = trailers.countByStatus(TrailerStatus.RUNNING);
        int availableSlots = Math.max(0, properties.getMaxConcurrentRuns() - (int) running);
        if (availableSlots > 0) {
            trailers.findByStatusOrderByIdAsc(TrailerStatus.QUEUED, PageRequest.of(0, availableSlots))
                    .forEach(t -> run(t, launcher::launch, "launch"));
        }
        trailers.findDueRunning(Instant.now(), PageRequest.of(0, 20)).forEach(t -> run(t, reconciler::reconcile, "reconcile"));
        trailers.findTop10ByStatusOrderByIdAsc(TrailerStatus.HARVESTING).forEach(t -> run(t, harvester::harvest, "harvest"));
    }

    private void run(BookTrailer t, Consumer<Long> step, String name) {
        try {
            step.accept(t.getId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("trailer {} {} lost a race; next tick re-reads it", t.getId(), name);
        } catch (RuntimeException e) {
            // Transient API/network errors: leave the row as is; the next tick retries. Launch failures that
            // persist are visible in logs and in the Console; the maxSessionAge guard bounds a stuck RUNNING row.
            log.error("trailer {} {} failed", t.getId(), name, e);
        }
    }
}
