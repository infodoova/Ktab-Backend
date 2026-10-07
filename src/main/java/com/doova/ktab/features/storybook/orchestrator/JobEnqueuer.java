package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JobEnqueuer {

    private final StorybookJobRepository jobs;

    public static String key(Long bookId, JobStep step, int pageIndex, int generation) {
        return bookId + ":" + step.name() + ":" + pageIndex + ":" + generation;
    }

    /**
     * Makes sure the work this job stands for is going to happen: creates the job if it is missing, or sets it going
     * again if it "succeeded" long ago without its result landing. A job that is pending, running or dead is left alone
     * (dead ones are revived by resume). Returns true when it changed anything.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean ensure(Long bookId, JobStep step, int pageIndex, int generation, java.time.Instant succeededBefore) {
        return enqueue(bookId, step, pageIndex, generation)
                || jobs.reviveSucceeded(key(bookId, step, pageIndex, generation), succeededBefore) == 1;
    }

    /** Must run inside the transaction that made the state change this job follows from. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(Long bookId, JobStep step, int pageIndex, int generation) {
        return jobs.insertIfAbsent(bookId, step.name(), pageIndex, generation,
                key(bookId, step, pageIndex, generation)) == 1;
    }
}
