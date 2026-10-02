package com.doova.ktab.features.storybook.image;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiImageProviderTest {

    @Test
    void configAsksForOnePortrait1kImage() {
        GenerateContentConfig config = GeminiImageProvider.buildConfig(new StorybookProperties.Image());

        assertThat(config.responseModalities().orElseThrow().toString()).contains("IMAGE");
        assertThat(config.imageConfig().orElseThrow().aspectRatio()).contains("3:4");
        assertThat(config.imageConfig().orElseThrow().imageSize()).contains("1K");
    }

    @Test
    void safetyFiltersAreNeverOff() {
        GenerateContentConfig config = GeminiImageProvider.buildConfig(new StorybookProperties.Image());

        List<SafetySetting> settings = config.safetySettings().orElseThrow();
        assertThat(settings).hasSize(4);
        assertThat(settings).allSatisfy(s ->
                assertThat(s.threshold().orElseThrow().toString()).contains("BLOCK_LOW_AND_ABOVE"));
    }

    @Test
    void referenceImagesComeBeforeThePrompt() {
        ImageRequest request = new ImageRequest("gemini-3.1-flash-image", "draw a cat",
                List.of(new ReferenceImage(new byte[]{1, 2}, "image/png"), new ReferenceImage(new byte[]{3}, "image/png")));

        Content content = GeminiImageProvider.buildContent(request);

        List<Part> parts = content.parts().orElseThrow();
        assertThat(parts).hasSize(3);
        assertThat(parts.get(0).inlineData()).isPresent();
        assertThat(parts.get(1).inlineData()).isPresent();
        assertThat(parts.get(2).text()).contains("draw a cat");
    }

    @Test
    void rejectsMoreThanFiveReferences() {
        List<ReferenceImage> six = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new ReferenceImage(new byte[]{1}, "image/png")).toList();
        assertThatThrownBy(() -> new ImageRequest("m", "p", six)).isInstanceOf(IllegalArgumentException.class);
    }
}
