package com.doova.ktab.features.storybook.image;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "STORYBOOK_LIVE_TESTS", matches = "true")
class GeminiImageProviderLiveTest {

    @Test
    void generatesASquareImage() throws Exception {
        Client client = Client.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location("global")
                .vertexAI(true)
                .credentials(GoogleCredentials.getApplicationDefault())
                .build();
        GeminiImageProvider provider = new GeminiImageProvider(client, new StorybookProperties());

        ImageResult result = provider.generate(new ImageRequest("gemini-3.1-flash-image-preview",
                "A friendly orange cat sitting in a sunny garden, children's book watercolour. No text.", List.of()));

        assertThat(result.bytes().length).isGreaterThan(10_000);
        assertThat(result.mimeType()).startsWith("image/");
    }
}
