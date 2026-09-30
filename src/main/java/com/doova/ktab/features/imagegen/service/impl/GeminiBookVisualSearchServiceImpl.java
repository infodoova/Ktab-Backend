package com.doova.ktab.features.imagegen.service.impl;

import com.doova.ktab.features.imagegen.config.ImageGenProperties;
import com.doova.ktab.features.imagegen.service.BookVisualSearchService;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiBookVisualSearchServiceImpl implements BookVisualSearchService {

    private final Client vertexGenAiClient;
    private final ImageGenProperties properties;
    private final com.doova.ktab.config.ai.GlobalAiProperties globalAi;

    @Override
    public String searchBookVisualLore(String bookTitle, String authorName) {
        if (!properties.isWebSearchEnabled() || bookTitle == null || bookTitle.isBlank()) {
            return null;
        }

        try {
            log.info("Triggering Google Web Search grounding for book: '{}' (author: '{}')", bookTitle, authorName);

            String authorHint = (authorName != null && !authorName.isBlank()) ? " by " + authorName : "";
            String queryPrompt = """
                    Search Google for the book "%s"%s.
                    Find its official book cover art styling, visual aesthetic, character appearances, architectural/world setting, and prominent color tones.
                    Summarize in 2-3 concise sentences the exact visual style and artistic atmosphere of this book to guide an illustration.
                    """.formatted(bookTitle.trim(), authorHint);

            Tool searchTool = Tool.builder()
                    .googleSearch(GoogleSearch.builder().build())
                    .build();

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .tools(List.of(searchTool))
                    .candidateCount(1)
                    .build();

            Content content = Content.builder()
                    .role("user")
                    .parts(List.of(Part.fromText(queryPrompt)))
                    .build();

            String model = (properties.getSearchModel() != null && !properties.getSearchModel().isBlank())
                    ? properties.getSearchModel()
                    : globalAi.getText().getSearch();

            GenerateContentResponse response = vertexGenAiClient.models.generateContent(
                    model,
                    List.of(content),
                    config
            );

            String result = response.text();
            if (result != null && !result.isBlank()) {
                log.info("Successfully retrieved Google Web Search grounding for book '{}'", bookTitle);
                return result.trim();
            }
        } catch (Exception e) {
            log.warn("Google Web Search grounding failed for book '{}' (continuing generation): {}", bookTitle, e.getMessage());
        }
        return null;
    }
}
