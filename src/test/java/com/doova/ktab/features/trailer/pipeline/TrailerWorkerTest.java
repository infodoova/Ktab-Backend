package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TrailerWorkerTest {

    private final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    private final TrailerLauncher launcher = mock(TrailerLauncher.class);
    private final TrailerReconciler reconciler = mock(TrailerReconciler.class);
    private final TrailerHarvester harvester = mock(TrailerHarvester.class);
    private final TrailerProperties properties = new TrailerProperties();

    private TrailerWorker worker;

    @BeforeEach
    void setUp() {
        properties.setMaxConcurrentRuns(2);
        worker = new TrailerWorker(trailers, launcher, reconciler, harvester, properties);
        when(trailers.findDueRunning(any(Instant.class), any())).thenReturn(Collections.emptyList());
        when(trailers.findTop10ByStatusOrderByIdAsc(TrailerStatus.HARVESTING)).thenReturn(Collections.emptyList());
    }

    @Test
    void tick_whenRunningEqualsMax_doesNotLaunchQueued() {
        when(trailers.countByStatus(TrailerStatus.RUNNING)).thenReturn(2L);

        worker.tick();

        verify(launcher, never()).launch(anyLong());
        verify(trailers, never()).findByStatusOrderByIdAsc(eq(TrailerStatus.QUEUED), any());
    }

    @Test
    void tick_whenRunningExceedsMax_doesNotLaunchQueued() {
        when(trailers.countByStatus(TrailerStatus.RUNNING)).thenReturn(3L);

        worker.tick();

        verify(launcher, never()).launch(anyLong());
        verify(trailers, never()).findByStatusOrderByIdAsc(eq(TrailerStatus.QUEUED), any());
    }

    @Test
    void tick_whenOneRunningAndMaxIsTwo_requestsExactlyOneSlot() {
        when(trailers.countByStatus(TrailerStatus.RUNNING)).thenReturn(1L);
        BookTrailer queued = new BookTrailer();
        queued.setId(55L);
        when(trailers.findByStatusOrderByIdAsc(eq(TrailerStatus.QUEUED), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(queued));

        worker.tick();

        verify(launcher).launch(55L);
    }

    @Test
    void tick_whenZeroRunningAndMaxIsTwo_requestsTwoSlots() {
        when(trailers.countByStatus(TrailerStatus.RUNNING)).thenReturn(0L);
        BookTrailer t1 = new BookTrailer();
        t1.setId(101L);
        BookTrailer t2 = new BookTrailer();
        t2.setId(102L);
        when(trailers.findByStatusOrderByIdAsc(eq(TrailerStatus.QUEUED), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(t1, t2));

        worker.tick();

        verify(launcher).launch(101L);
        verify(launcher).launch(102L);
    }
}
