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

    /** Must run inside the transaction that made the state change this job follows from. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(Long bookId, JobStep step, int pageIndex, int generation) {
        return jobs.insertIfAbsent(bookId, step.name(), pageIndex, generation,
                key(bookId, step, pageIndex, generation)) == 1;
    }
}
