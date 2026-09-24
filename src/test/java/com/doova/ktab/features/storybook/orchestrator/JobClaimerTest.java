package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobClaimerTest {

    @Mock
    private StorybookJobRepository jobs;
    @Mock
    private EntityManager entityManager;

    private JobClaimer claimer;

    @BeforeEach
    void setUp() {
        claimer = new JobClaimer(jobs, entityManager);
    }

    @Test
    void claim_limitZeroOrNegative_returnsEmptyWithoutDbCalls() {
        assertThat(claimer.claim("worker-1", 0)).isEmpty();
        assertThat(claimer.claim("worker-1", -1)).isEmpty();
        verifyNoInteractions(jobs, entityManager);
    }

    @Test
    void claim_noDueJobs_returnsEmpty() {
        when(jobs.lockDueJobIds(5)).thenReturn(List.of());

        List<StorybookJob> result = claimer.claim("worker-1", 5);

        assertThat(result).isEmpty();
        verify(jobs).lockDueJobIds(5);
        verify(jobs, never()).markRunning(any(), any());
    }

    @Test
    void claim_dueJobsFound_marksRunningAndReturnsReloaded() {
        List<Long> ids = List.of(10L, 20L);
        when(jobs.lockDueJobIds(2)).thenReturn(ids);
        StorybookJob j1 = new StorybookJob();
        j1.setId(10L);
        StorybookJob j2 = new StorybookJob();
        j2.setId(20L);
        when(jobs.findAllById(ids)).thenReturn(List.of(j1, j2));

        List<StorybookJob> result = claimer.claim("worker-1", 2);

        assertThat(result).containsExactly(j1, j2);
        verify(jobs).markRunning(ids, "worker-1");
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    void releaseStale_delegatesToRepository() {
        when(jobs.releaseStaleRunning(any(Instant.class))).thenReturn(3);

        int count = claimer.releaseStale(Duration.ofMinutes(10));

        assertThat(count).isEqualTo(3);
        verify(jobs).releaseStaleRunning(any(Instant.class));
    }
}
