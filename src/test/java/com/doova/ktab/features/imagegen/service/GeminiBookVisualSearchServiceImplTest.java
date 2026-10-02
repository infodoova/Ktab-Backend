package com.doova.ktab.features.imagegen.service;

import com.doova.ktab.features.imagegen.config.ImageGenProperties;
import com.doova.ktab.features.imagegen.service.impl.GeminiBookVisualSearchServiceImpl;
import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiBookVisualSearchServiceImplTest {

    @Mock
    private Client vertexGenAiClient;

    @Mock
    private Models models;

    @Mock
    private GenerateContentResponse response;

    private ImageGenProperties properties;
    private GeminiBookVisualSearchServiceImpl visualSearchService;

    @BeforeEach
    void setUp() {
        properties = new ImageGenProperties();
        properties.setWebSearchEnabled(true);
        properties.setSearchModel("gemini-2.5-flash");

        org.springframework.test.util.ReflectionTestUtils.setField(vertexGenAiClient, "models", models);
        visualSearchService = new GeminiBookVisualSearchServiceImpl(vertexGenAiClient, properties, new com.doova.ktab.config.ai.GlobalAiProperties());
    }

    @Test
    @DisplayName("searchBookVisualLore should trigger Google search and return visual summary when enabled")
    void searchBookVisualLore_enabled_returnsSearchSummary() {
        when(response.text()).thenReturn("Official cover features a grand sandstone gate with sapphire desert skies.");
        when(models.generateContent(eq("gemini-2.5-flash"), anyList(), any(GenerateContentConfig.class)))
                .thenReturn(response);

        String result = visualSearchService.searchBookVisualLore("The Desert Citadel", "Tariq Al-Mansoor");

        assertNotNull(result);
        assertTrue(result.contains("sandstone gate"));
        verify(models).generateContent(eq("gemini-2.5-flash"), anyList(), any(GenerateContentConfig.class));
    }

    @Test
    @DisplayName("searchBookVisualLore should return null when web search is disabled in config")
    void searchBookVisualLore_disabled_returnsNull() {
        properties.setWebSearchEnabled(false);

        String result = visualSearchService.searchBookVisualLore("The Desert Citadel", "Tariq Al-Mansoor");

        assertNull(result);
        verifyNoInteractions(models);
    }

    @Test
    @DisplayName("searchBookVisualLore should gracefully return null on exception without interrupting workflow")
    void searchBookVisualLore_apiException_returnsNullGracefully() {
        when(models.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
                .thenThrow(new RuntimeException("Network timeout during search"));

        String result = visualSearchService.searchBookVisualLore("The Desert Citadel", "Tariq Al-Mansoor");

        assertNull(result);
    }
}
