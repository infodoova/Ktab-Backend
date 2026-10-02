package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.image.ReferenceImage;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisualQaTest {

    private static byte[] png(int size) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void sendsReferencesFirstAndCandidateLastAsSmallJpegs() throws Exception {
        FakeLlmGateway llm = new FakeLlmGateway();
        llm.enqueue(new VisualQaResponse(true, false, true, true, List.of()));
        VisualQa qa = new VisualQa(llm, new PromptLibrary(), new StorybookProperties());

        var call = qa.check(png(2048), List.of(new ReferenceImage(png(2048), "image/png"),
                new ReferenceImage(png(1024), "image/png")), "The CHILD waves.");

        assertThat(call.value().passed()).isTrue();
        var request = llm.requests().get(0);
        assertThat(request.purpose()).isEqualTo(LlmPurpose.VISUAL_QA);
        assertThat(request.images()).hasSize(3).allSatisfy(i -> assertThat(i.mediaType()).isEqualTo("image/jpeg"));
        assertThat(request.user()).contains("The CHILD waves.").contains("Image 3 is the illustration to check");
    }

    @Test
    void anyFailedCheckFailsThePage() {
        assertThat(new VisualQaResponse(true, true, true, true, List.of("letters on a sign")).passed()).isFalse();
        assertThat(new VisualQaResponse(false, false, true, true, List.of()).passed()).isFalse();
        assertThat(new VisualQaResponse(true, false, false, true, List.of()).passed()).isFalse();
        assertThat(new VisualQaResponse(true, false, true, false, List.of()).passed()).isFalse();
    }

    @Test
    void aFailedQaCallIsNeverTreatedAsAPass() throws Exception {
        var llm = org.mockito.Mockito.mock(com.doova.ktab.features.storybook.llm.LlmGateway.class);
        org.mockito.Mockito.when(llm.call(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.doova.ktab.features.storybook.llm.LlmCallFailedException("429 rate limit", true, null));
        VisualQa qa = new VisualQa(llm, new PromptLibrary(), new StorybookProperties());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> qa.check(png(512),
                        List.of(new ReferenceImage(png(512), "image/png")), "The CHILD waves."))
                .isInstanceOfSatisfying(com.doova.ktab.features.storybook.llm.LlmCallFailedException.class,
                        e -> assertThat(e.retryable()).isTrue());
    }
}
