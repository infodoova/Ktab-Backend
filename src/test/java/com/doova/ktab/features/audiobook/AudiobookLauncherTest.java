package com.doova.ktab.features.audiobook;

import com.doova.ktab.features.studio.config.StudioProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AudiobookLauncherTest {

    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final JobExplorer explorer = mock(JobExplorer.class);
    private final Job studioJob = mock(Job.class);
    private final Job nativeJob = mock(Job.class);
    private final StudioProperties studio = new StudioProperties();
    private AudiobookLauncher launcher;

    @BeforeEach
    void setUp() throws Exception {
        launcher = new AudiobookLauncher(jobLauncher, explorer, studioJob, nativeJob, studio);
        when(explorer.findRunningJobExecutions(any())).thenReturn(Set.of());
        when(jobLauncher.run(any(Job.class), any(JobParameters.class))).thenReturn(mock(JobExecution.class));
    }

    private static JobExecution runningFor(long bookId) {
        JobExecution e = mock(JobExecution.class);
        when(e.getJobParameters()).thenReturn(new JobParametersBuilder().addLong("bookId", bookId).toJobParameters());
        return e;
    }

    @Test
    void theNativeJobRunsWhileStudioIsOffAndTheStudioJobWhenItIsOn() throws Exception {
        assertThat(launcher.activeJobName()).isEqualTo("nativeAudiobookJob");
        launcher.launch(7L);
        verify(jobLauncher).run(eq(nativeJob), any(JobParameters.class));

        studio.setEnabled(true);
        assertThat(launcher.activeJobName()).isEqualTo("studioAudiobookJob");
        launcher.launch(7L);
        verify(jobLauncher).run(eq(studioJob), any(JobParameters.class));
    }

    @Test
    void theLaunchCarriesTheBookIdAndAFreshRunId() throws Exception {
        launcher.launch(7L);
        org.mockito.ArgumentCaptor<JobParameters> p = org.mockito.ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(eq(nativeJob), p.capture());
        assertThat(p.getValue().getLong("bookId")).isEqualTo(7L);
        assertThat(p.getValue().getLong("run.id")).isNotNull();
    }

    @Test
    void aRunningJobOfEitherKindBlocksANewOneForThatBookOnly() {
        JobExecution nativeRun = runningFor(7L);
        JobExecution studioRun = runningFor(7L);
        when(explorer.findRunningJobExecutions("nativeAudiobookJob")).thenReturn(Set.of(nativeRun));
        assertThat(launcher.runningFor(7L)).isPresent();
        assertThat(launcher.runningFor(8L)).isEmpty();

        when(explorer.findRunningJobExecutions("nativeAudiobookJob")).thenReturn(Set.of());
        when(explorer.findRunningJobExecutions("studioAudiobookJob")).thenReturn(Set.of(studioRun));
        assertThat(launcher.runningFor(7L)).isPresent();
    }
}
