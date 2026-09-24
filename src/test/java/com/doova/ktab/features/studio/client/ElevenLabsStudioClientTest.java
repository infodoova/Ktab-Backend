package com.doova.ktab.features.studio.client;

import com.doova.ktab.features.studio.client.dto.*;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ElevenLabsStudioClient Unit Tests")
class ElevenLabsStudioClientTest {

    private HttpClient mockHttpClient;
    private StudioProperties props;
    private ObjectMapper objectMapper;
    private ElevenLabsStudioClient client;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        props = new StudioProperties();
        props.setApiKey("test-key-12345");
        props.setBaseUrl("https://api.elevenlabs.io");
        objectMapper = new ObjectMapper();
        client = new ElevenLabsStudioClient(props, objectMapper, mockHttpClient);
    }

    @Test
    @DisplayName("createProject builds JSON request and deserializes response")
    void createProject_validResponse_returnsProject() throws Exception {
        String json = """
                {
                    "project_id": "proj-abc-123",
                    "name": "My Great Book",
                    "default_model_id": "eleven_v2_multilingual",
                    "default_title_voice_id": "voice-1",
                    "default_paragraph_voice_id": "voice-2",
                    "quality_preset": "high",
                    "state": "created"
                }
                """;

        HttpResponse<String> response = mockResponse(200, json);
        doReturn(response).when(mockHttpClient).send(any(), any());

        StudioProjectResponse result = client.createProject("My Great Book", "https://s3.example.com/test.pdf", null, "voice-1", "voice-2");

        assertNotNull(result);
        assertEquals("proj-abc-123", result.projectId());
        assertEquals("My Great Book", result.name());
        assertEquals("eleven_v2_multilingual", result.defaultModelId());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest sent = captor.getValue();
        assertEquals("POST", sent.method());
        assertEquals(URI.create("https://api.elevenlabs.io/v1/studio/projects"), sent.uri());
        assertEquals("test-key-12345", sent.headers().firstValue("xi-api-key").orElse(null));
    }

    @Test
    @DisplayName("listProjects unpacks envelope { projects: [...] }")
    void listProjects_envelopeResponse_returnsList() throws Exception {
        String json = """
                {
                    "projects": [
                        { "project_id": "p1", "name": "Book 1" },
                        { "project_id": "p2", "name": "Book 2" }
                    ]
                }
                """;

        HttpResponse<String> response = mockResponse(200, json);
        doReturn(response).when(mockHttpClient).send(any(), any());

        List<StudioProjectResponse> list = client.listProjects();
        assertEquals(2, list.size());
        assertEquals("p1", list.get(0).projectId());
        assertEquals("p2", list.get(1).projectId());
    }

    @Test
    @DisplayName("listChapters unpacks envelope { chapters: [...] }")
    void listChapters_envelopeResponse_returnsList() throws Exception {
        String json = """
                {
                    "chapters": [
                        {
                            "chapter_id": "ch-1",
                            "name": "Chapter 1",
                            "last_conversion_date_unix": 1700000000,
                            "conversion_progress": 1.0,
                            "can_be_downloaded": true
                        },
                        {
                            "chapter_id": "ch-2",
                            "name": "Chapter 2",
                            "conversion_progress": 0.5,
                            "can_be_downloaded": false
                        }
                    ]
                }
                """;

        HttpResponse<String> response = mockResponse(200, json);
        doReturn(response).when(mockHttpClient).send(any(), any());

        List<StudioChapterSummary> chapters = client.listChapters("proj-123");
        assertEquals(2, chapters.size());
        assertEquals("ch-1", chapters.get(0).chapterId());
        assertTrue(chapters.get(0).isFullyConverted());
        assertFalse(chapters.get(1).isFullyConverted());
    }

    @Test
    @DisplayName("getChapter returns detailed chapter with blocks and nodes")
    void getChapter_validResponse_returnsDetail() throws Exception {
        String json = """
                {
                    "chapter_id": "ch-1",
                    "name": "Chapter 1",
                    "content": {
                        "blocks": [
                            {
                                "block_id": "b1",
                                "type": "h1",
                                "nodes": [ { "node_id": "n1", "text": "Header Title" } ]
                            },
                            {
                                "block_id": "b2",
                                "type": "p",
                                "nodes": [ { "node_id": "n2", "text": "Paragraph content." } ]
                            }
                        ]
                    }
                }
                """;

        HttpResponse<String> response = mockResponse(200, json);
        doReturn(response).when(mockHttpClient).send(any(), any());

        StudioChapterDetail detail = client.getChapter("proj-123", "ch-1");
        assertNotNull(detail);
        assertEquals("ch-1", detail.chapterId());
        assertEquals(2, detail.content().blocks().size());
        assertEquals("h1", detail.content().blocks().get(0).type());
        assertEquals("Header Title", detail.content().blocks().get(0).nodes().get(0).text());
    }

    @Test
    @DisplayName("convertProject fires POST /convert")
    void convertProject_validResponse_succeeds() throws Exception {
        HttpResponse<String> response = mockResponse(200, "{}");
        doReturn(response).when(mockHttpClient).send(any(), any());

        assertDoesNotThrow(() -> client.convertProject("proj-123"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        assertEquals(URI.create("https://api.elevenlabs.io/v1/studio/projects/proj-123/convert"), captor.getValue().uri());
    }

    @Test
    @DisplayName("deleteProject fires DELETE")
    void deleteProject_validResponse_succeeds() throws Exception {
        HttpResponse<String> response = mockResponse(200, "{}");
        doReturn(response).when(mockHttpClient).send(any(), any());

        assertDoesNotThrow(() -> client.deleteProject("proj-123"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        assertEquals("DELETE", captor.getValue().method());
    }

    @Test
    @DisplayName("HTTP 429 without Retry-After throws StudioApiException.Retryable")
    void checkStatus_status429_throwsRetryable() throws Exception {
        HttpResponse<String> response = mockResponse(429, "Too many requests");
        doReturn(response).when(mockHttpClient).send(any(), any());

        StudioApiException.Retryable ex = assertThrows(StudioApiException.Retryable.class,
                () -> client.getProject("proj-123"));
        assertEquals(429, ex.statusCode());
        assertFalse(ex instanceof ElevenLabsStudioClient.RetryableWithDelay);
    }

    @Test
    @DisplayName("HTTP 429 with Retry-After header throws RetryableWithDelay")
    void checkStatus_status429WithRetryAfter_throwsRetryableWithDelay() throws Exception {
        HttpResponse<String> response = mockResponse(429, "Rate limited", Map.of("Retry-After", List.of("15")));
        doReturn(response).when(mockHttpClient).send(any(), any());

        StudioApiException.Retryable ex = assertThrows(StudioApiException.Retryable.class,
                () -> client.getProject("proj-123"));
        assertInstanceOf(ElevenLabsStudioClient.RetryableWithDelay.class, ex);
        ElevenLabsStudioClient.RetryableWithDelay withDelay = (ElevenLabsStudioClient.RetryableWithDelay) ex;
        assertEquals(15000L, withDelay.retryAfterMs());
    }

    @Test
    @DisplayName("HTTP 500/503 throws StudioApiException.Retryable")
    void checkStatus_status500_throwsRetryable() throws Exception {
        HttpResponse<String> response = mockResponse(503, "Service Unavailable");
        doReturn(response).when(mockHttpClient).send(any(), any());

        StudioApiException.Retryable ex = assertThrows(StudioApiException.Retryable.class,
                () -> client.getProject("proj-123"));
        assertEquals(503, ex.statusCode());
    }

    @Test
    @DisplayName("HTTP 404 throws StudioApiException.Fatal")
    void checkStatus_status404_throwsFatal() throws Exception {
        HttpResponse<String> response = mockResponse(404, "Not Found");
        doReturn(response).when(mockHttpClient).send(any(), any());

        StudioApiException.Fatal ex = assertThrows(StudioApiException.Fatal.class,
                () -> client.getProject("proj-123"));
        assertEquals(404, ex.statusCode());
    }

    @Test
    @DisplayName("HTTP 400 throws StudioApiException.Fatal")
    void checkStatus_status400_throwsFatal() throws Exception {
        HttpResponse<String> response = mockResponse(400, "Bad Request: invalid voice ID");
        doReturn(response).when(mockHttpClient).send(any(), any());

        StudioApiException.Fatal ex = assertThrows(StudioApiException.Fatal.class,
                () -> client.getProject("proj-123"));
        assertEquals(400, ex.statusCode());
    }

    @Test
    @DisplayName("Network IOException throws StudioApiException.Retryable")
    void send_networkException_throwsRetryable() throws Exception {
        doThrow(new IOException("Connection reset by peer")).when(mockHttpClient).send(any(), any());

        StudioApiException.Retryable ex = assertThrows(StudioApiException.Retryable.class,
                () -> client.getProject("proj-123"));
        assertEquals(-1, ex.statusCode());
        assertTrue(ex.getMessage().contains("Connection reset"));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int statusCode, String body) {
        return mockResponse(statusCode, body, Map.of());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int statusCode, String body, Map<String, List<String>> headers) {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(statusCode);
        when(resp.body()).thenReturn(body);
        when(resp.uri()).thenReturn(URI.create("https://api.elevenlabs.io/test"));
        when(resp.headers()).thenReturn(HttpHeaders.of(headers, (k, v) -> true));
        return resp;
    }
}
