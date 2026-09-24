package com.doova.ktab.features.storybook.illustration;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PageRegenerationServiceTest {

    private final StorybookAccessGuard guard = mock(StorybookAccessGuard.class);
    private final StorybookPageRepository pages = mock(StorybookPageRepository.class);
    private final JobEnqueuer enqueuer = mock(JobEnqueuer.class);
    private final StorybookStateMachine stateMachine = mock(StorybookStateMachine.class);
    private final StorybookProperties properties = new StorybookProperties();
    private PageRegenerationService service;
    private final User owner = new User();

    @BeforeEach
    void setUp() {
        owner.setId(1L);
        service = new PageRegenerationService(guard, pages, enqueuer, stateMachine, properties);
    }

    @Test
    void regeneratesPageSuccessfully() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.READY);
        book.setPageRegenerations(0);
        when(guard.requireOwned(10L, owner)).thenReturn(book);

        StorybookPage page = new StorybookPage();
        page.setId(50L);
        page.setPageIndex(3);
        page.setGeneration(1);
        when(pages.findByStorybook_IdAndPageIndex(10L, 3)).thenReturn(Optional.of(page));

        service.regenerate(owner, 10L, 3);

        assertThat(book.getPageRegenerations()).isEqualTo(1);
        assertThat(page.getGeneration()).isEqualTo(2);
        assertThat(page.getRoundStartGeneration()).isEqualTo(2);
        verify(stateMachine).transition(book, StorybookStatus.ILLUSTRATING);
        verify(enqueuer).enqueue(10L, JobStep.ILLUSTRATE_PAGE, 3, 2);
    }

    @Test
    void rejectsWhenNotReady() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        when(guard.requireOwned(10L, owner)).thenReturn(book);

        assertThatThrownBy(() -> service.regenerate(owner, 10L, 3))
                .isInstanceOf(StorybookStateConflictException.class);
    }

    @Test
    void throws404WhenPageNotFound() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.READY);
        when(guard.requireOwned(10L, owner)).thenReturn(book);
        when(pages.findByStorybook_IdAndPageIndex(10L, 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.regenerate(owner, 10L, 99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsWhenLimitReached() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.READY);
        book.setPageRegenerations(properties.getLimits().getPageRegenerationsPerBook());
        when(guard.requireOwned(10L, owner)).thenReturn(book);

        StorybookPage page = new StorybookPage();
        page.setPageIndex(2);
        when(pages.findByStorybook_IdAndPageIndex(10L, 2)).thenReturn(Optional.of(page));

        assertThatThrownBy(() -> service.regenerate(owner, 10L, 2))
                .isInstanceOf(BadRequestException.class);
    }
}
