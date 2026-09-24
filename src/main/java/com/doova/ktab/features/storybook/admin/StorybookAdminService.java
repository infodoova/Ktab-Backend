package com.doova.ktab.features.storybook.admin;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.billing.StorybookCreditPort;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.illustration.IllustrationPersistence;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StorybookAdminService {

    private final StorybookPageImageRepository images;
    private final StorybookPageRepository pages;
    private final StorybookRepository books;
    private final IllustrationPersistence illustration;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final StorybookCreditPort credits;
    private final StorybookProperties properties;
    private final FileStorageService storage;

    @Transactional(readOnly = true)
    public List<FlaggedPageView> flaggedPages() {
        return images.findFlaggedCurrentImages().stream().map(i -> {
            StorybookPage p = i.getPage();
            return new FlaggedPageView(p.getStorybook().getId(), p.getId(), p.getPageIndex(), i.getGeneration(),
                    storage.getFileUrl(i.getImageKey(), UrlStrategy.SIGNED), p.getSceneEn(),
                    i.getQaResult() == null || i.getQaResult().problems() == null ? List.of() : i.getQaResult().problems());
        }).toList();
    }

    @Transactional
    public void accept(Long pageId) {
        StorybookPage page = pages.findById(pageId).orElseThrow();
        StorybookPageImage image = page.getCurrentImage();
        if (image == null || image.getStatus() != PageImageStatus.FLAGGED) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        image.setStatus(PageImageStatus.ACCEPTED_BY_ADMIN);
        illustration.advanceIfDone(page.getStorybook().getId());
    }

    @Transactional
    public void regenerate(Long pageId) {
        StorybookPage page = pages.findById(pageId).orElseThrow();
        Storybook book = books.findByIdForUpdate(page.getStorybook().getId()).orElseThrow();
        if (book.getStatus() != StorybookStatus.QA || page.getCurrentImage() == null
                || page.getCurrentImage().getStatus() != PageImageStatus.FLAGGED) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        stateMachine.transition(book, StorybookStatus.ILLUSTRATING);
        int next = page.getGeneration() + 1;
        page.setGeneration(next);
        page.setRoundStartGeneration(next - (properties.getImage().getMaxGenerations() - 1));
        enqueuer.enqueue(book.getId(), JobStep.ILLUSTRATE_PAGE, page.getPageIndex(), next);
    }

    @Transactional
    public void grantCredits(Long userId, int units) {
        credits.grant(userId, units);
    }
}
