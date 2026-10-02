package com.doova.ktab.features.audiobook;

import com.doova.ktab.features.studio.config.StudioProperties;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The single place that decides which audiobook job runs: Studio's while KTAB_STUDIO_ENABLED is true, Ktab's own
 * otherwise. The flag is read at launch time only, so a running job always finishes on the pipeline it started on.
 */
@Component
public class AudiobookLauncher {

    static final String STUDIO_JOB = "studioAudiobookJob";
    static final String NATIVE_JOB = "nativeAudiobookJob";

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job studioAudiobookJob;
    private final Job nativeAudiobookJob;
    private final StudioProperties studioProperties;

    public AudiobookLauncher(JobLauncher jobLauncher, JobExplorer jobExplorer,
                             @Qualifier("studioAudiobookJob") Job studioAudiobookJob,
                             @Qualifier("nativeAudiobookJob") Job nativeAudiobookJob, StudioProperties studioProperties) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.studioAudiobookJob = studioAudiobookJob;
        this.nativeAudiobookJob = nativeAudiobookJob;
        this.studioProperties = studioProperties;
    }

    public String activeJobName() {
        return studioProperties.isEnabled() ? STUDIO_JOB : NATIVE_JOB;
    }

    /** A running audiobook job of either kind for this book. */
    public Optional<JobExecution> runningFor(Long bookId) {
        return jobExplorer.findRunningJobExecutions(STUDIO_JOB).stream()
                .filter(e -> bookId.equals(e.getJobParameters().getLong("bookId")))
                .findFirst()
                .or(() -> jobExplorer.findRunningJobExecutions(NATIVE_JOB).stream()
                        .filter(e -> bookId.equals(e.getJobParameters().getLong("bookId")))
                        .findFirst());
    }

    public JobExecution launch(Long bookId) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("bookId", bookId)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        return jobLauncher.run(studioProperties.isEnabled() ? studioAudiobookJob : nativeAudiobookJob, params);
    }
}
