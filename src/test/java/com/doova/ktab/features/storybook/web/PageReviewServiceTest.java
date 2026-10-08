package com.doova.ktab.features.storybook.web;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.admin.FlaggedPageView;
import com.doova.ktab.features.storybook.admin.StorybookAdminService;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.illustration.VisualQaResponse;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PageReviewServiceTest {

    private final StorybookAccessGuard guard = mock(StorybookAccessGuard.class);
    private final StorybookRepository books = mock(StorybookRepository.class);
    private final StorybookPageRepository pages = mock(StorybookPageRepository.class);
    private final StorybookPageImageRepository images = mock(StorybookPageImageRepository.class);
    private final StorybookAdminService admin = mock(StorybookAdminService.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final StorybookProperties properties = new StorybookProperties();
    private final PageReviewService service = new PageReviewService(guard, books, pages, images, admin, storage, properties);

    private final User owner = new User();
    private final Storybook book = new Storybook();
    private final StorybookPage page = new StorybookPage();

    @BeforeEach
    void setUp() {
        owner.setId(1L);
        book.setId(42L);
        book.setStatus(StorybookStatus.QA);
        page.setId(728L);
        page.setPageIndex(15);
        when(guard.requireOwned(42L, owner)).thenReturn(book);
        when(books.findByIdForUpdate(42L)).thenReturn(Optional.of(book));
        when(pages.findByStorybook_IdAndPageIndex(42L, 15)).thenReturn(Optional.of(page));
        when(storage.getFileUrl(any(), any())).thenAnswer(inv -> "https://signed/" + inv.getArgument(0));
    }

    @Test
    void theOwnerSeesTheirFlaggedPagesWithThePictureAndTheProblems() {
        StorybookPageImage image = new StorybookPageImage();
        image.setPage(page);
        image.setGeneration(2);
        image.setImageKey("p15.png");
        image.setWebImageKey("p15.web.jpg");
        image.setQaResult(new VisualQaResponse(true, false, false, true, true, List.of("Rocks intrude into the bottom text area.")));
        page.setSceneEn("Crossing the stream.");
        when(images.findFlaggedCurrentImagesByStorybookId(42L)).thenReturn(List.of(image));

        List<FlaggedPageView> result = service.flagged(owner, 42L);

        assertThat(result).singleElement().satisfies(v -> {
            assertThat(v.bookId()).isEqualTo(42L);
            assertThat(v.pageId()).isEqualTo(728L);
            assertThat(v.pageIndex()).isEqualTo(15);
            assertThat(v.imageUrl()).isEqualTo("https://signed/p15.web.jpg");   // the small copy, as for readers
            assertThat(v.problems()).containsExactly("Rocks intrude into the bottom text area.");
        });
    }

    @Test
    void somebodyElsesBookIsNotFoundForEveryAction() {
        User stranger = new User();
        stranger.setId(2L);
        when(guard.requireOwned(42L, stranger)).thenThrow(new ResourceNotFoundException(com.doova.ktab.enums.message.ApiMessageKey.STORYBOOK_NOT_FOUND));

        assertThatThrownBy(() -> service.flagged(stranger, 42L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.accept(stranger, 42L, 15)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.regenerate(stranger, 42L, 15)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(admin);
    }

    @Test
    void aPageThatIsNotInTheBookIsNotFound() {
        when(pages.findByStorybook_IdAndPageIndex(42L, 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(owner, 42L, 99)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.regenerate(owner, 42L, 99)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(admin);
    }

    @Test
    void acceptingHandsThePageToTheSameActionTheAdminUses() {
        service.accept(owner, 42L, 15);

        verify(admin).accept(728L);
    }

    @Test
    void redrawingCountsAgainstTheBooksPageRegenerations() {
        book.setPageRegenerations(1);

        service.regenerate(owner, 42L, 15);

        verify(admin).regenerate(728L);
        assertThat(book.getPageRegenerations()).isEqualTo(2);
    }

    @Test
    void whenTheLimitIsUsedUpNothingIsRedrawn() {
        book.setPageRegenerations(properties.getLimits().getPageRegenerationsPerBook());

        assertThatThrownBy(() -> service.regenerate(owner, 42L, 15)).isInstanceOf(BadRequestException.class);
        verify(admin, never()).regenerate(any());
        assertThat(book.getPageRegenerations()).isEqualTo(properties.getLimits().getPageRegenerationsPerBook());
    }

    @Test
    void aBookThatIsNotInQaCannotBeRedrawnThisWay() {
        book.setStatus(StorybookStatus.READY);

        assertThatThrownBy(() -> service.regenerate(owner, 42L, 15)).isInstanceOf(StorybookStateConflictException.class);
        verify(admin, never()).regenerate(any());
    }

    @Test
    void aRefusalFromTheAdminActionDoesNotUseUpARedraw() {
        doThrow(new StorybookStateConflictException(com.doova.ktab.enums.message.ApiMessageKey.STORYBOOK_INVALID_STATE))
                .when(admin).regenerate(728L);

        assertThatThrownBy(() -> service.regenerate(owner, 42L, 15)).isInstanceOf(StorybookStateConflictException.class);
        assertThat(book.getPageRegenerations()).isZero();
    }
}
