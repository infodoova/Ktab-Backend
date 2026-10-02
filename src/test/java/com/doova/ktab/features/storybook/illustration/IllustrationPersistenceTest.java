package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IllustrationPersistenceTest {

    private final StorybookRepository books = mock(StorybookRepository.class);
    private final StorybookCharacterRepository characters = mock(StorybookCharacterRepository.class);
    private final StorybookPageRepository pages = mock(StorybookPageRepository.class);
    private final StorybookPageImageRepository images = mock(StorybookPageImageRepository.class);
    private final JobEnqueuer enqueuer = mock(JobEnqueuer.class);
    private final StorybookStateMachine stateMachine = mock(StorybookStateMachine.class);
    private final StorybookProperties properties = new StorybookProperties();
    private IllustrationPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new IllustrationPersistence(books, characters, pages, images, enqueuer, stateMachine, properties);
    }

    @Test
    void passedVerdictMarksPageAndAdvancesToRenderingWhenAllPagesDone() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        when(books.findByIdForUpdate(10L)).thenReturn(Optional.of(book));

        StorybookPage page = new StorybookPage();
        page.setId(100L);
        page.setPageIndex(0);
        page.setGeneration(1);
        page.setRoundStartGeneration(1);
        when(pages.findById(100L)).thenReturn(Optional.of(page));

        StorybookPageImage image = new StorybookPageImage();
        image.setId(200L);
        image.setStatus(PageImageStatus.GENERATED);
        when(images.findById(200L)).thenReturn(Optional.of(image));

        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(page));
        when(images.findByPage_IdAndGeneration(100L, 1)).thenReturn(Optional.of(image));

        VisualQaResponse passVerdict = new VisualQaResponse(true, false, true, true, List.of());
        persistence.recordQa(10L, 100L, 200L, 1, passVerdict);

        assertThat(image.getStatus()).isEqualTo(PageImageStatus.QA_PASSED);
        assertThat(page.getCurrentImage()).isEqualTo(image);
        verify(stateMachine).transition(book, StorybookStatus.RENDERING);
        verify(enqueuer).enqueue(10L, JobStep.RENDER_PDF, -1, 0);
    }

    @Test
    void failedVerdictUnderMaxGenerationsEnqueuesNextIllustration() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        when(books.findByIdForUpdate(10L)).thenReturn(Optional.of(book));

        StorybookPage page = new StorybookPage();
        page.setId(100L);
        page.setPageIndex(2);
        page.setGeneration(1);
        page.setRoundStartGeneration(1);
        when(pages.findById(100L)).thenReturn(Optional.of(page));

        StorybookPageImage image = new StorybookPageImage();
        image.setId(200L);
        image.setStatus(PageImageStatus.GENERATED);
        when(images.findById(200L)).thenReturn(Optional.of(image));

        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(page));
        // after QA_FAILED, generation becomes 2 so findByPage_IdAndGeneration(100L, 2) is empty, meaning in progress
        when(images.findByPage_IdAndGeneration(100L, 2)).thenReturn(Optional.empty());

        VisualQaResponse failVerdict = new VisualQaResponse(false, false, true, true, List.of("bad hands"));
        persistence.recordQa(10L, 100L, 200L, 1, failVerdict);

        assertThat(image.getStatus()).isEqualTo(PageImageStatus.QA_FAILED);
        assertThat(page.getGeneration()).isEqualTo(2);
        verify(enqueuer).enqueue(10L, JobStep.ILLUSTRATE_PAGE, 2, 2);
        verify(stateMachine, never()).transition(any(), any());
    }

    @Test
    void failedVerdictAtMaxGenerationsFlagsImageAndParksInQa() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        when(books.findByIdForUpdate(10L)).thenReturn(Optional.of(book));

        StorybookPage page = new StorybookPage();
        page.setId(100L);
        page.setPageIndex(2);
        page.setGeneration(4); // maxGenerations is 4 (roundStart=1, attempt=4)
        page.setRoundStartGeneration(1);
        when(pages.findById(100L)).thenReturn(Optional.of(page));

        StorybookPageImage image = new StorybookPageImage();
        image.setId(200L);
        image.setStatus(PageImageStatus.GENERATED);
        when(images.findById(200L)).thenReturn(Optional.of(image));

        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(page));
        when(images.findByPage_IdAndGeneration(100L, 4)).thenReturn(Optional.of(image));

        VisualQaResponse failVerdict = new VisualQaResponse(false, false, true, true, List.of("persistent defect"));
        persistence.recordQa(10L, 100L, 200L, 4, failVerdict);

        assertThat(image.getStatus()).isEqualTo(PageImageStatus.FLAGGED);
        assertThat(page.getCurrentImage()).isEqualTo(image);
        verify(stateMachine).transition(book, StorybookStatus.QA);
    }

    @Test
    void aFlaggedCoverStillReleasesTheStoryPagesSoNothingDeadlocks() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        when(books.findByIdForUpdate(10L)).thenReturn(Optional.of(book));

        StorybookPage cover = new StorybookPage();
        cover.setId(100L);
        cover.setPageIndex(0);
        cover.setKind(com.doova.ktab.features.storybook.enums.PageKind.COVER);
        cover.setGeneration(4);
        cover.setRoundStartGeneration(1);
        StorybookPage story = new StorybookPage();
        story.setId(101L);
        story.setPageIndex(1);
        story.setKind(com.doova.ktab.features.storybook.enums.PageKind.STORY);
        story.setGeneration(1);
        when(pages.findById(100L)).thenReturn(Optional.of(cover));
        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(cover, story));

        StorybookPageImage image = new StorybookPageImage();
        image.setId(200L);
        image.setStatus(PageImageStatus.GENERATED);
        when(images.findById(200L)).thenReturn(Optional.of(image));
        when(images.findByPage_IdAndGeneration(100L, 4)).thenReturn(Optional.of(image));

        persistence.recordQa(10L, 100L, 200L, 4, new VisualQaResponse(false, false, true, true, List.of("hair")));

        assertThat(image.getStatus()).isEqualTo(PageImageStatus.FLAGGED);
        verify(enqueuer).enqueue(10L, JobStep.ILLUSTRATE_PAGE, 1, 1);
    }
}
