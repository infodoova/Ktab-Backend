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
    private final org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);
    private IllustrationPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new IllustrationPersistence(books, characters, pages, images, enqueuer, stateMachine, properties, events);
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

    private StorybookPage page(long id, int index, PageKind kind, int generation, int roundStart) {
        StorybookPage p = new StorybookPage();
        p.setId(id);
        p.setPageIndex(index);
        p.setKind(kind);
        p.setGeneration(generation);
        p.setRoundStartGeneration(roundStart);
        return p;
    }

    private StorybookPageImage image(long id, int generation, PageImageStatus status, VisualQaResponse qa) {
        StorybookPageImage i = new StorybookPageImage();
        i.setId(id);
        i.setGeneration(generation);
        i.setStatus(status);
        i.setQaResult(qa);
        return i;
    }

    private Storybook drawingBook() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        when(books.findByIdForUpdate(10L)).thenReturn(Optional.of(book));
        return book;
    }

    private static final VisualQaResponse WRONG_OUTFIT = new VisualQaResponse(false, true, false, true, true, List.of("outfit"));

    @Test
    void aPageFailingTheSameChecksTwiceInARowIsFlaggedInsteadOfRedrawn() {
        drawingBook();
        StorybookPage page = page(101L, 1, PageKind.STORY, 2, 1);
        StorybookPageImage first = image(201L, 1, PageImageStatus.QA_FAILED, WRONG_OUTFIT);
        StorybookPageImage second = image(202L, 2, PageImageStatus.GENERATED, null);
        when(pages.findById(101L)).thenReturn(Optional.of(page));
        when(images.findById(202L)).thenReturn(Optional.of(second));
        when(images.findByPage_IdAndGeneration(101L, 1)).thenReturn(Optional.of(first));
        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(page));

        persistence.recordQa(10L, 101L, 202L, 2, WRONG_OUTFIT);

        assertThat(second.getStatus()).isEqualTo(PageImageStatus.FLAGGED);
        verify(enqueuer, never()).enqueue(eq(10L), eq(JobStep.ILLUSTRATE_PAGE), anyInt(), anyInt());
    }

    @Test
    void aDifferentFailureStillGetsAnotherAttempt() {
        drawingBook();
        StorybookPage page = page(101L, 1, PageKind.STORY, 2, 1);
        StorybookPageImage first = image(201L, 1, PageImageStatus.QA_FAILED,
                new VisualQaResponse(true, false, false, true, true, List.of("scene")));
        StorybookPageImage second = image(202L, 2, PageImageStatus.GENERATED, null);
        when(pages.findById(101L)).thenReturn(Optional.of(page));
        when(images.findById(202L)).thenReturn(Optional.of(second));
        when(images.findByPage_IdAndGeneration(101L, 1)).thenReturn(Optional.of(first));
        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(page));

        persistence.recordQa(10L, 101L, 202L, 2, WRONG_OUTFIT);

        assertThat(second.getStatus()).isEqualTo(PageImageStatus.QA_FAILED);
        verify(enqueuer).enqueue(10L, JobStep.ILLUSTRATE_PAGE, 1, 3);
    }

    @Test
    void theStopOnRepeatsCanBeTurnedOff() {
        properties.getImage().setRepeatFailureLimit(0);
        drawingBook();
        StorybookPage page = page(101L, 1, PageKind.STORY, 2, 1);
        StorybookPageImage first = image(201L, 1, PageImageStatus.QA_FAILED, WRONG_OUTFIT);
        StorybookPageImage second = image(202L, 2, PageImageStatus.GENERATED, null);
        when(pages.findById(101L)).thenReturn(Optional.of(page));
        when(images.findById(202L)).thenReturn(Optional.of(second));
        when(images.findByPage_IdAndGeneration(101L, 1)).thenReturn(Optional.of(first));
        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(page));

        persistence.recordQa(10L, 101L, 202L, 2, WRONG_OUTFIT);

        verify(enqueuer).enqueue(10L, JobStep.ILLUSTRATE_PAGE, 1, 3);
    }

    private static final java.time.Instant LONG_AGO = java.time.Instant.parse("2026-10-07T08:00:00Z");

    @Test
    void reconcileRestartsADrawingJobForAPageWithNoImage() {
        Storybook book = drawingBook();
        StorybookPage cover = page(100L, 0, PageKind.COVER, 1, 1);
        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(cover));
        when(images.findByPage_IdAndGeneration(100L, 1)).thenReturn(Optional.empty());
        when(enqueuer.ensure(10L, JobStep.ILLUSTRATE_PAGE, 0, 1, LONG_AGO)).thenReturn(true);

        assertThat(persistence.reconcile(10L, LONG_AGO)).isEqualTo(1);

        verify(enqueuer).ensure(10L, JobStep.ILLUSTRATE_PAGE, 0, 1, LONG_AGO);
        assertThat(book.getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
    }

    @Test
    void reconcileRestartsTheCheckForAnImageNoJobIsGoingToJudge() {
        drawingBook();
        StorybookPage cover = page(100L, 0, PageKind.COVER, 1, 1);
        StorybookPage story = page(101L, 1, PageKind.STORY, 2, 1);
        StorybookPageImage coverImage = image(200L, 1, PageImageStatus.QA_PASSED, null);
        StorybookPageImage storyImage = image(202L, 2, PageImageStatus.GENERATED, null);
        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(cover, story));
        when(images.findByPage_IdAndGeneration(100L, 1)).thenReturn(Optional.of(coverImage));
        when(images.findByPage_IdAndGeneration(101L, 2)).thenReturn(Optional.of(storyImage));
        when(enqueuer.ensure(10L, JobStep.QA_PAGE, 1, 2, LONG_AGO)).thenReturn(true);

        assertThat(persistence.reconcile(10L, LONG_AGO)).isEqualTo(1);

        verify(enqueuer, never()).ensure(eq(10L), any(), eq(0), anyInt(), any());
    }

    @Test
    void reconcileLeavesStoryPagesAloneUntilTheCoverHasSettled() {
        drawingBook();
        StorybookPage cover = page(100L, 0, PageKind.COVER, 1, 1);
        StorybookPage story = page(101L, 1, PageKind.STORY, 1, 1);
        StorybookPageImage coverImage = image(200L, 1, PageImageStatus.GENERATED, null);
        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(cover, story));
        when(images.findByPage_IdAndGeneration(100L, 1)).thenReturn(Optional.of(coverImage));

        persistence.reconcile(10L, LONG_AGO);

        verify(enqueuer).ensure(10L, JobStep.QA_PAGE, 0, 1, LONG_AGO);
        verify(enqueuer, never()).ensure(eq(10L), any(), eq(1), anyInt(), any());
    }

    @Test
    void reconcileDoesNothingToABookThatIsNotDrawing() {
        Storybook book = drawingBook();
        book.setStatus(StorybookStatus.QA);

        assertThat(persistence.reconcile(10L, LONG_AGO)).isZero();

        verifyNoInteractions(enqueuer);
    }
}
