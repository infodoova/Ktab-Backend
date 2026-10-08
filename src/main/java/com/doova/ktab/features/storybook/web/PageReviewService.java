package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.admin.FlaggedPageView;
import com.doova.ktab.features.storybook.admin.StorybookAdminService;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The owner of a storybook decides about the pages the automatic checks could not settle (status QA): accept a picture
 * as it is, or have it drawn again. The same actions exist for admins in {@link StorybookAdminService}; this adds the
 * ownership check and the owner's limit on redraws, which cost money.
 */
@Service
@RequiredArgsConstructor
public class PageReviewService {

    private final StorybookAccessGuard guard;
    private final StorybookRepository books;
    private final StorybookPageRepository pages;
    private final StorybookPageImageRepository images;
    private final StorybookAdminService adminService;
    private final FileStorageService storage;
    private final StorybookProperties properties;

    /** The pages of the owner's book that wait for a decision. */
    @Transactional(readOnly = true)
    public List<FlaggedPageView> flagged(User owner, Long bookId) {
        guard.requireOwned(bookId, owner);
        return images.findFlaggedCurrentImagesByStorybookId(bookId).stream().map(i -> {
            StorybookPage p = i.getPage();
            return new FlaggedPageView(bookId, p.getId(), p.getPageIndex(), i.getGeneration(),
                    storage.getFileUrl(StorybookViewMapper.readerKey(i), UrlStrategy.SIGNED), p.getSceneEn(),
                    i.getQaResult() == null || i.getQaResult().problems() == null ? List.of() : i.getQaResult().problems());
        }).toList();
    }

    /** Keeps the picture of a flagged page as it is. Once no page is waiting, the book goes on to the PDF. */
    @Transactional
    public void accept(User owner, Long bookId, int pageIndex) {
        StorybookPage page = ownedPage(owner, bookId, pageIndex);
        adminService.accept(page.getId());
    }

    /** Draws a flagged page again. Counts against the same per-book limit as redrawing a page of a finished book. */
    @Transactional
    public void regenerate(User owner, Long bookId, int pageIndex) {
        StorybookPage page = ownedPage(owner, bookId, pageIndex);
        Storybook book = books.findByIdForUpdate(bookId).orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.STORYBOOK_NOT_FOUND));
        if (book.getStatus() != StorybookStatus.QA) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        if (book.getPageRegenerations() >= properties.getLimits().getPageRegenerationsPerBook()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_LIMIT_REACHED);
        }
        // The admin action also checks that the page is flagged; if it refuses, this transaction rolls the count back.
        adminService.regenerate(page.getId());
        book.setPageRegenerations(book.getPageRegenerations() + 1);
    }

    private StorybookPage ownedPage(User owner, Long bookId, int pageIndex) {
        guard.requireOwned(bookId, owner);
        return pages.findByStorybook_IdAndPageIndex(bookId, pageIndex)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.STORYBOOK_NOT_FOUND));
    }
}
