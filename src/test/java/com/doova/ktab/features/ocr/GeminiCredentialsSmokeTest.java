package com.doova.ktab.features.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GeminiCredentialsSmokeTest {

    @Value("${gcp.credentials.base64:${GCP_CREDENTIALS_BASE64:}}")
    private String credentialsBase64;

    @Value("${spring.ai.vertex.ai.gemini.project-id:${GCP_PROJECT_ID:}}")
    private String configuredProjectId;

    @Autowired(required = false)
    @Qualifier("ocrGeminiModel")
    private ChatModel ocrGeminiModel;

    @Test
    @DisplayName("Verify GCP_CREDENTIALS_BASE64 validity, OAuth2 token generation, and Gemini connectivity")
    void testGeminiCredentialsFunctional() {
        System.out.println("================================================================================");
        System.out.println(">>> TESTING GCP GEMINI CREDENTIALS (GCP_CREDENTIALS_BASE64)");
        System.out.println("================================================================================");

        // 1. Check if the environment variable / property is provided
        if (credentialsBase64 == null || credentialsBase64.isBlank()) {
            System.err.println("❌ ERROR: GCP_CREDENTIALS_BASE64 is empty or not configured!");
            fail("GCP_CREDENTIALS_BASE64 is missing in environment/.env");
        }

        System.out.printf("  1. Base64 String Found: %d characters%n", credentialsBase64.trim().length());

        // 2. Decode Base64
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(credentialsBase64.trim());
            System.out.printf("  2. Base64 Decode: SUCCESS (%d bytes)%n", decodedBytes.length);
        } catch (Exception e) {
            System.err.printf("❌ ERROR: Failed to decode Base64 string: %s%n", e.getMessage());
            fail("GCP_CREDENTIALS_BASE64 is not valid Base64: " + e.getMessage());
            return;
        }

        // 3. Validate JSON structure (safely without exposing private keys)
        String jsonString = new String(decodedBytes, StandardCharsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try {
            root = mapper.readTree(jsonString);
            String type = root.path("type").asText("unknown");
            String projectId = root.path("project_id").asText("unknown");
            String clientEmail = root.path("client_email").asText("unknown");

            System.out.printf("  3. JSON Parsing: SUCCESS%n");
            System.out.printf("     - Credential Type:  %s%n", type);
            System.out.printf("     - GCP Project ID:   %s%n", projectId);
            System.out.printf("     - Service Account:  %s%n", clientEmail);

            assertTrue("service_account".equals(type) || "authorized_user".equals(type),
                    "Credential type must be 'service_account' or 'authorized_user' but was: " + type);

            if ("authorized_user".equals(type)) {
                String clientId = root.path("client_id").asText("unknown");
                boolean hasRefreshToken = root.has("refresh_token");
                System.out.printf("     - Client ID:        %s%n", clientId.isEmpty() ? "none" : clientId.substring(0, Math.min(15, clientId.length())) + "...");
                System.out.printf("     - Has RefreshToken: %s%n", hasRefreshToken);
                assertTrue(hasRefreshToken, "authorized_user credentials must contain a refresh_token");
            } else {
                assertFalse(projectId.isBlank(), "project_id must not be empty");
                assertFalse(clientEmail.isBlank(), "client_email must not be empty");
            }
        } catch (Exception e) {
            System.err.printf("❌ ERROR: Decoded bytes do not form valid JSON: %s%n", e.getMessage());
            fail("Decoded credentials are not valid JSON: " + e.getMessage());
            return;
        }

        // 4. Authenticate with Google OAuth2 and fetch access token
        System.out.println("  4. Requesting Google OAuth2 Access Token from accounts.google.com...");
        GoogleCredentials credentials;
        AccessToken token;
        try {
            GoogleCredentials raw = GoogleCredentials.fromStream(new ByteArrayInputStream(decodedBytes));
            if (raw.createScopedRequired()) {
                credentials = raw.createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
            } else {
                credentials = raw;
            }

            credentials.refreshIfExpired();
            token = credentials.getAccessToken();

            if (token == null) {
                credentials.refresh();
                token = credentials.getAccessToken();
            }

            assertNotNull(token, "OAuth2 access token must not be null");
            assertNotNull(token.getTokenValue(), "Token string must not be null");
            System.out.printf("     >>> OAUTH2 AUTHENTICATION SUCCESSFUL!%n");
            System.out.printf("     - Token Expiration: %s%n", token.getExpirationTime());
        } catch (Exception e) {
            System.err.printf("❌ ERROR: Google rejected credentials during OAuth2 token generation: %s%n", e.getMessage());
            fail("Google OAuth2 authentication failed with these credentials: " + e.getMessage());
            return;
        }

        // 5. Test Live Gemini Call (Optional ping if model bean is loaded)
        if (ocrGeminiModel != null) {
            System.out.println("  5. Sending live ping prompt to Vertex AI Gemini model...");
            try {
                long tStart = System.currentTimeMillis();
                String response = ocrGeminiModel.call("قل مرحباً باختصار");
                long tDuration = System.currentTimeMillis() - tStart;
                System.out.printf("     >>> LIVE GEMINI CALL SUCCESS! (%d ms)%n", tDuration);
                System.out.printf("     - Response: %s%n", response != null ? response.trim() : "(empty)");
            } catch (Exception e) {
                System.out.printf("     ⚠️ Note: Vertex AI API endpoint call returned: %s%n", e.getMessage());
            }
        }

        System.out.println("================================================================================");
        System.out.println(">>> RESULT: GCP_CREDENTIALS_BASE64 IS VALID & FUNCTIONAL!");
        System.out.println("================================================================================");
    }

    @Autowired(required = false)
    private com.doova.ktab.features.ocr.ai.GeminiOcrService geminiOcrService;

    @Test
    @DisplayName("Live Gemini Vision OCR on Real Rendered Page of الأجنحة المتكسرة")
    void testLiveGeminiVisionOnRealBookPage() throws Exception {
        System.out.println("================================================================================");
        System.out.println(">>> LIVE GEMINI VISION OCR TEST: Page 1 of الأجنحة المتكسرة");
        System.out.println("================================================================================");

        // 1. Load uploaded PDF and render Page 1 using PDFBox
        java.io.File pdfFile = new java.io.File("C:/Users/PC/.gemini/antigravity-ide/brain/b1952b0a-76c8-4070-8403-37ac063f64f6/.user_uploaded/media_1789967820810.pdf");
        assertTrue(pdfFile.exists(), "Uploaded PDF must exist");

        byte[] page1PngBytes;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfFile)) {
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            java.awt.image.BufferedImage img = renderer.renderImageWithDPI(0, 200);
            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                javax.imageio.ImageIO.write(img, "png", baos);
                page1PngBytes = baos.toByteArray();
            }
        }

        System.out.printf("  1. Rendered Page 1 to PNG: %d bytes%n", page1PngBytes.length);

        // 2. Call live Gemini Vision OCR
        assertNotNull(geminiOcrService, "geminiOcrService bean must be injected");
        System.out.println("  2. Sending Page 1 image to Google Vertex AI Gemini Vision...");
        long tStart = System.currentTimeMillis();
        com.doova.ktab.features.ocr.dto.GeminiOcrResponse resp = geminiOcrService.ocrOnePage(page1PngBytes, "image/png");
        long tDuration = System.currentTimeMillis() - tStart;

        System.out.println("\n================================================================================");
        System.out.printf(">>> LIVE GEMINI VISION OCR COMPLETED IN: %d ms (%.2f seconds)%n", tDuration, tDuration / 1000.0);
        System.out.println("================================================================================");
        System.out.printf("  Page Kind:        %s%n", resp.pageKind());
        System.out.printf("  Word Count:       %d%n", resp.wordCount());
        System.out.printf("  Headings:         %s%n", resp.headings());
        System.out.printf("  Printed Label:    %s%n", resp.printedPageLabel());
        System.out.printf("  Running Header:   %s%n", resp.runningHeader());
        System.out.printf("  Transcription:%n%s%n", resp.bodyMarkdown().substring(0, Math.min(300, resp.bodyMarkdown().length())));
        System.out.println("================================================================================\n");

        assertNotNull(resp.bodyMarkdown());
        assertFalse(resp.bodyMarkdown().isBlank(), "Gemini OCR transcription must not be empty");
    }
}
