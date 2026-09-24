package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StorybookResumeService {

    private final StorybookAccessGuard guard;
    private final StorybookStateMachine stateMachine;
    private final StorybookJobRepository jobs;

    @Transactional
    public void resume(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        if (book.getStatus() != StorybookStatus.FAILED || book.getFailedFromStatus() == null) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        stateMachine.transition(book, book.getFailedFromStatus());
        jobs.reviveDeadJobs(bookId);
    }
}
