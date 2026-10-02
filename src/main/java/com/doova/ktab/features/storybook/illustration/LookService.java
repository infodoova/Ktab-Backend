package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.CharacterSheetStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LookService {

    private final StorybookAccessGuard guard;
    private final StorybookCharacterRepository characters;
    private final StorybookPageRepository pages;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final StorybookProperties properties;

    @Transactional
    public void regenerate(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        StorybookCharacter child = requireSheetReady(book);
        if (book.getLookRegenerations() >= properties.getLimits().getLookRegenerations()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_LIMIT_REACHED);
        }
        book.setLookRegenerations(book.getLookRegenerations() + 1);
        enqueuer.enqueue(bookId, JobStep.CHARACTER_SHEET, -1, child.getSheetVersion() + 1);
    }

    /** Approval gate 2: page illustration (the bulk of the image cost) starts only after this. */
    @Transactional
    public void approve(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        StorybookCharacter child = requireSheetReady(book);
        book.setLookApprovedAt(Instant.now());
        child.setSheetStatus(CharacterSheetStatus.APPROVED);
        stateMachine.transition(book, StorybookStatus.ILLUSTRATING);
        for (StorybookPage page : pages.findByStorybook_IdOrderByPageIndexAsc(bookId)) {
            page.setGeneration(1);
            page.setRoundStartGeneration(1);
            if (page.getPageIndex() == 0) {
                // the cover is drawn and checked first; its verdict releases the story pages (see IllustrationPersistence.recordQa)
                enqueuer.enqueue(bookId, JobStep.ILLUSTRATE_PAGE, page.getPageIndex(), 1);
            }
        }
    }

    private StorybookCharacter requireSheetReady(Storybook book) {
        StorybookCharacter child = characters.findByStorybook_IdAndKind(book.getId(), CharacterKind.CHILD).orElseThrow();
        boolean ready = book.getStatus() == StorybookStatus.CHARACTER_READY
                && book.getLookApprovedAt() == null
                && child.getSheetStatus() == CharacterSheetStatus.GENERATED
                && child.getSheetVersion() == book.getLookRegenerations() + 1;
        if (!ready) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        return child;
    }
}
