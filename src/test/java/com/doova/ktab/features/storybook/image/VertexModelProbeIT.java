package com.doova.ktab.features.storybook.image;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Probe whether gemini-3-pro-image-preview is accessible on ktab-prod Vertex AI.
 * Run with: mvnw test -Dtest=VertexModelProbeIT
 */
@DisplayName("Vertex AI Model Availability Probe")
class VertexModelProbeIT {

    private static final String PROJECT = "ktab-prod";
    private static final String LOCATION = "global";
    private static final String PROMPT = "A simple blue circle on a white background.";

    @Test
    @DisplayName("Probe: gemini-3-pro-image-preview exists and is callable")
    void probe_gemini3ProImagePreview_available() throws Exception {
        probe("gemini-3-pro-image-preview");
    }

    @Test
    @DisplayName("Probe: gemini-3-pro-image (GA name, no -preview) is callable")
    void probe_gemini3ProImage_available() throws Exception {
        probe("gemini-3-pro-image");
    }

    @Test
    @DisplayName("Probe: gemini-3.1-pro-image exists")
    void probe_gemini31ProImage_available() throws Exception {
        probe("gemini-3.1-pro-image");
    }

    @Test
    @DisplayName("Probe: gemini-3.1-pro-image-preview exists")
    void probe_gemini31ProImagePreview_available() throws Exception {
        probe("gemini-3.1-pro-image-preview");
    }

    @Test
    @DisplayName("Probe: gemini-3.5-pro-image exists")
    void probe_gemini35ProImage_available() throws Exception {
        probe("gemini-3.5-pro-image");
    }

    @Test
    @DisplayName("Probe: gemini-3.5-flash-image exists")
    void probe_gemini35FlashImage_available() throws Exception {
        probe("gemini-3.5-flash-image");
    }

    @Test
    @DisplayName("Probe: gemini-2.0-flash-exp:generateImage exists")
    void probe_gemini20FlashExp_available() throws Exception {
        probe("gemini-2.0-flash-exp:generateImage");
    }

    @Test
    @DisplayName("Probe: imagen-3.0-generate-002 exists")
    void probe_imagen3_available() throws Exception {
        probe("imagen-3.0-generate-002");
    }

    @Test
    @DisplayName("Probe: gemini-3.1-flash-image is callable (GA name)")
    void probe_gemini31FlashImage_available() throws Exception {
        probe("gemini-3.1-flash-image");
    }

    @Test
    @DisplayName("Probe: gemini-3.1-flash-image-preview is callable (preview name)")
    void probe_gemini31FlashImagePreview_available() throws Exception {
        probe("gemini-3.1-flash-image-preview");
    }

    // -------------------------------------------------------------------------

    private void probe(String modelName) throws Exception {
        Path credPath = Path.of("gcp-credentials.json");
        Assumptions.assumeTrue(Files.exists(credPath),
                "gcp-credentials.json not found — skipping live probe");

        GoogleCredentials credentials;
        try (InputStream in = Files.newInputStream(credPath)) {
            credentials = GoogleCredentials.fromStream(in);
        }

        Client client = Client.builder()
                .project(PROJECT)
                .location(LOCATION)
                .vertexAI(true)
                .credentials(credentials)
                .build();

        StorybookProperties.Image cfg = new StorybookProperties().getImage();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities(List.of("IMAGE"))
                .imageConfig(ImageConfig.builder()
                        .aspectRatio(cfg.getAspectRatio())
                        .imageSize(cfg.getImageSize())
                        .build())
                .candidateCount(1)
                .build();

        Content content = Content.builder()
                .role("user")
                .parts(List.of(Part.fromText(PROMPT)))
                .build();

        System.out.printf("%n[PROBE] Calling model: %s on project=%s location=%s%n",
                modelName, PROJECT, LOCATION);

        try {
            var response = client.models.generateContent(modelName, List.of(content), config);
            var parts = response.parts();
            boolean hasImage = parts != null && parts.stream()
                    .anyMatch(p -> p.inlineData().isPresent());

            System.out.printf("[PROBE] ✅ Model %s is AVAILABLE — response parts=%d hasImage=%b%n%n",
                    modelName, parts == null ? 0 : parts.size(), hasImage);

        } catch (ApiException e) {
            System.out.printf("[PROBE] ❌ Model %s returned HTTP %d: %s%n%n",
                    modelName, e.code(), e.getMessage());
            if (e.code() == 404) {
                throw new AssertionError("Model '" + modelName + "' does NOT exist on " +
                        PROJECT + "/" + LOCATION + " (HTTP 404): " + e.getMessage(), e);
            } else if (e.code() == 401 || e.code() == 403) {
                Assumptions.assumeTrue(false, "Auth failure (HTTP " + e.code() + ") — skipping: " + e.getMessage());
            } else {
                throw e;
            }
        } catch (RuntimeException e) {
            String msg = rootMessage(e);
            if (msg != null && msg.contains("invalid_grant")) {
                Assumptions.assumeTrue(false, "GCP OAuth expired (invalid_grant) — refresh credentials and retry");
            }
            throw e;
        }
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage();
    }
}
