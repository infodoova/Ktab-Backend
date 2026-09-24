package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StoryApprovalService {

    private final StorybookAccessGuard guard;
    private final JobEnqueuer enqueuer;
    private final com.doova.ktab.features.storybook.billing.StorybookCreditPort credits;
    private final com.doova.ktab.features.storybook.config.StorybookProperties properties;

    /** Approval gate 1. The first paid image (the character sheet) is enqueued only after this. */
    @Transactional
    public void approveStory(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        if (book.getStatus() != StorybookStatus.STORY_READY || book.getStoryApprovedAt() != null) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        if (properties.getCredits().isRequired()) {
            credits.reserve(owner.getId(), bookId, properties.getCredits().getUnitsPerBook());
        }
        book.setStoryApprovedAt(Instant.now());
        enqueuer.enqueue(bookId, JobStep.CHARACTER_SHEET, -1, 1);
    }
}
