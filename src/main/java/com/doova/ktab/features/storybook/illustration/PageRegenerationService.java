package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PageRegenerationService {

    private final StorybookAccessGuard guard;
    private final StorybookPageRepository pages;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final StorybookProperties properties;

    /** Spec "Final review": the parent can regenerate individual pages a limited number of times (D9). */
    @Transactional
    public void regenerate(User owner, Long bookId, int pageIndex) {
        Storybook book = guard.requireOwned(bookId, owner);
        if (book.getStatus() != StorybookStatus.READY) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        StorybookPage page = pages.findByStorybook_IdAndPageIndex(bookId, pageIndex)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.STORYBOOK_NOT_FOUND));
        if (book.getPageRegenerations() >= properties.getLimits().getPageRegenerationsPerBook()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_LIMIT_REACHED);
        }
        book.setPageRegenerations(book.getPageRegenerations() + 1);
        stateMachine.transition(book, StorybookStatus.ILLUSTRATING);
        int next = page.getGeneration() + 1;
        page.setGeneration(next);
        page.setRoundStartGeneration(next);
        enqueuer.enqueue(bookId, JobStep.ILLUSTRATE_PAGE, pageIndex, next);
    }
}
