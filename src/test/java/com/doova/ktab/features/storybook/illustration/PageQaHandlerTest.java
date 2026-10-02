package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PageQaHandlerTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final IllustrationPersistence persistence = mock(IllustrationPersistence.class);
    private final PageQaHandler handler = new PageQaHandler(
            new VisualQa(llm, new PromptLibrary(), new StorybookProperties()), store, persistence,
            mock(AiCallLedger.class), new StyleReferences());

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static StorybookJob job() {
        StorybookJob j = new StorybookJob();
        j.setId(1L);
        j.setStorybookId(9L);
        j.setStep(JobStep.QA_PAGE);
        j.setPageIndex(2);
        j.setGeneration(1);
        return j;
    }

    private static QaContext ctx(PageImageStatus status, StorybookStatus book) {
        return new QaContext(9L, book, 100L, 500L, status, "img", "The CHILD reads.",
                List.of(new CharacterInScene("CHILD", "calm")), "child-sheet", null, ArtStyle.SOFT_WATERCOLOR);
    }

    @Test
    void checksAndRecordsTheVerdict() throws Exception {
        when(persistence.qaContext(9L, 2, 1)).thenReturn(ctx(PageImageStatus.GENERATED, StorybookStatus.ILLUSTRATING));
        when(store.get("img")).thenReturn(png());
        when(store.get("child-sheet")).thenReturn(png());
        VisualQaResponse verdict = new VisualQaResponse(true, false, true, true, List.of());
        llm.enqueue(verdict);

        assertThat(handler.handle(job()).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(persistence).recordQa(9L, 100L, 500L, 1, verdict);
    }

    @Test
    void alreadyJudgedImagesAreSkipped() {
        when(persistence.qaContext(9L, 2, 1)).thenReturn(ctx(PageImageStatus.QA_PASSED, StorybookStatus.ILLUSTRATING));
        handler.handle(job());
        assertThat(llm.requests()).isEmpty();
        verify(persistence, never()).recordQa(any(), any(), any(), anyInt(), any());
    }

    @Test
    void staleBookIsSkipped() {
        when(persistence.qaContext(9L, 2, 1)).thenReturn(ctx(PageImageStatus.GENERATED, StorybookStatus.FAILED));
        handler.handle(job());
        assertThat(llm.requests()).isEmpty();
    }

    @Test
    void theCheckerIsNotAskedToJudgeAnOutfitTheSceneNamesInTheWrongColour() throws Exception {
        QaContext dirty = new QaContext(9L, StorybookStatus.ILLUSTRATING, 100L, 500L, PageImageStatus.GENERATED, "img",
                "The CHILD reads. CHILD wears her lavender hijab; the lower third is a rug.",
                List.of(new CharacterInScene("CHILD", "calm")), "child-sheet", null, ArtStyle.SOFT_WATERCOLOR);
        when(persistence.qaContext(9L, 2, 1)).thenReturn(dirty);
        when(store.get("img")).thenReturn(png());
        when(store.get("child-sheet")).thenReturn(png());
        llm.enqueue(new VisualQaResponse(true, false, true, true, List.of()));

        handler.handle(job());

        assertThat(llm.requests().get(0).user()).doesNotContain("lavender").contains("The CHILD reads");
    }
}
