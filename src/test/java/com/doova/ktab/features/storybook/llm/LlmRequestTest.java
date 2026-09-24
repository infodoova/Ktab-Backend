package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.enums.LlmPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRequestTest {

    record Answer(String text) {}

    @Test
    void factoryDefaultsToNoImagesAndSixteenThousandTokens() {
        LlmRequest<Answer> r = LlmRequest.of(LlmPurpose.MODERATION, "sys", "user", Answer.class);
        assertThat(r.images()).isEmpty();
        assertThat(r.maxTokens()).isEqualTo(16000);
    }

    @Test
    void imagesAreDefensivelyCopied() {
        List<LlmImage> images = new ArrayList<>(List.of(new LlmImage(new byte[]{1}, "image/png")));
        LlmRequest<Answer> r = LlmRequest.of(LlmPurpose.VISUAL_QA, "sys", "user", Answer.class).withImages(images);
        images.clear();
        assertThat(r.images()).hasSize(1);
    }

    @Test
    void rejectsUnsupportedImageTypes() {
        assertThatThrownBy(() -> new LlmImage(new byte[]{1}, "image/webp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresSystemAndUserText() {
        assertThatThrownBy(() -> LlmRequest.of(LlmPurpose.MODERATION, " ", "user", Answer.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
