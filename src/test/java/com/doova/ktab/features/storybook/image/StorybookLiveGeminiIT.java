package com.doova.ktab.features.storybook.image;

import com.doova.ktab.features.storybook.character.CharacterPrompts;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Live Vertex AI Gemini Image Generation Integration Test")
class StorybookLiveGeminiIT {

    @Test
    void generate_liveVertexGeminiFlashImage_generatesRealImage() throws Exception {
        Path credPath = Path.of("gcp-credentials.json");
        Assumptions.assumeTrue(Files.exists(credPath), "gcp-credentials.json does not exist; skipping live test");

        GoogleCredentials credentials;
        try (InputStream in = Files.newInputStream(credPath)) {
            credentials = GoogleCredentials.fromStream(in);
        }

        StorybookProperties properties = new StorybookProperties();
        // 3:4 portrait orientation with 1K resolution as specified
        properties.getImage().setAspectRatio("3:4");
        properties.getImage().setImageSize("1K");

        Client client = Client.builder()
                .project("ktab-prod")
                .location("global")
                .vertexAI(true)
                .credentials(credentials)
                .build();

        GeminiImageProvider provider = new GeminiImageProvider(client, properties);

        String prompt = CharacterPrompts.sheet(ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE);
        ImageRequest request = new ImageRequest("gemini-3.1-flash-image", prompt, List.of());

        System.out.println("Calling Vertex AI Gemini 3.1 Flash Image model live...");
        ImageResult result;
        try {
            result = provider.generate(request);
        } catch (Exception e) {
            Throwable curr = e;
            boolean invalidGrant = false;
            while (curr != null) {
                if (curr.getMessage() != null && curr.getMessage().contains("invalid_grant")) {
                    invalidGrant = true;
                    break;
                }
                curr = curr.getCause();
            }
            if (invalidGrant) {
                System.err.println("\n[!] Vertex AI OAuth error: Google's OAuth endpoint returned 'invalid_grant'.");
                System.err.println("    The refresh_token provided in the base64 credentials has expired or was revoked.");
                System.err.println("    To fix: re-authenticate via 'gcloud auth application-default login' or use a GCP Service Account key.\n");
                Assumptions.assumeTrue(false, "Google OAuth refresh_token rejected ('invalid_grant')");
                return;
            }
            throw e;
        }

        assertThat(result).isNotNull();
        assertThat(result.bytes()).isNotEmpty();
        assertThat(result.mimeType()).startsWith("image/");
        assertThat(result.latencyMs()).isGreaterThan(0);

        Path outDir = Path.of("target/live-test");
        Files.createDirectories(outDir);
        Path imgPath = outDir.resolve("gemini-character-sheet.png");
        Files.write(imgPath, result.bytes());

        System.out.printf(
                "%n=== LIVE VERTEX AI GEMINI CALL SUCCESSFUL ===%nModel: %s%nLatency: %d ms%nMime type: %s%nImage size: %d bytes%nSaved to: %s%n=============================================%n",
                result.model(),
                result.latencyMs(),
                result.mimeType(),
                result.bytes().length,
                imgPath.toAbsolutePath()
        );
    }
}
