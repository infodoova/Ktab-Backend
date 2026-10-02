package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.story.CharacterBibleResponse;
import com.doova.ktab.features.storybook.support.LiveTestCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Live LLM Integration Test Against Real API")
class StorybookLiveLlmIT {

    @Test
    void call_liveOpenAiApi_returnsValidStructuredResponse() {
        String apiKey = LiveTestCredentials.get("OPENAI_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY is not configured; skipping live test");

        StorybookProperties properties = new StorybookProperties();
        // Use live standard model available on public API, or override via env
        String liveModel = LiveTestCredentials.get("STORYBOOK_LIVE_MODEL");
        if (liveModel == null || liveModel.isBlank()) {
            liveModel = "gpt-4o-mini";
        }
        properties.getLlm().setModel(liveModel);

        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiLlmGateway gateway = new OpenAiLlmGateway(openAiApi, properties, new ObjectMapper());

        String systemPrompt = """
                You are an expert children's book visual designer.
                Output ONLY valid JSON matching this schema:
                {
                  "characters": [
                    {
                      "name": "string",
                      "role": "string",
                      "visualLock": "string",
                      "clothing": "string",
                      "personality": "string"
                    }
                  ],
                  "visualStyleNotes": "string",
                  "summary": "string"
                }
                """;

        String userPrompt = "Create character visual specifications for a 6-year-old boy named Sami who loves cats and is going on his first day of school.";

        LlmRequest<CharacterBibleResponse> request = new LlmRequest<>(
                LlmPurpose.CHARACTER_BIBLE,
                systemPrompt,
                userPrompt,
                List.of(),
                CharacterBibleResponse.class,
                1500
        );

        LlmCall<CharacterBibleResponse> response = gateway.call(request);

        assertThat(response).isNotNull();
        assertThat(response.model()).isEqualTo(liveModel);
        assertThat(response.inputTokens()).isGreaterThan(0);
        assertThat(response.outputTokens()).isGreaterThan(0);
        assertThat(response.latencyMs()).isGreaterThan(0);

        CharacterBibleResponse bible = response.value();
        assertThat(bible).isNotNull();
        assertThat(bible.characters()).isNotEmpty();
        assertThat(bible.visualStyleNotes()).isNotBlank();
        assertThat(bible.summary()).isNotBlank();

        System.out.printf(
                "%n=== LIVE OPENAI CALL SUCCESSFUL ===%nModel: %s%nLatency: %d ms%nInput tokens: %d, Output tokens: %d%nCharacter count: %d%nFirst character: %s (%s)%nVisual style: %s%n===================================%n",
                response.model(),
                response.latencyMs(),
                response.inputTokens(),
                response.outputTokens(),
                bible.characters().size(),
                bible.characters().get(0).name(),
                bible.characters().get(0).role(),
                bible.visualStyleNotes()
        );
    }

    @Test
    void call_liveStoryBlueprint_generatesArabicBeatsAndParsesSuccessfully() {
        String apiKey = LiveTestCredentials.get("OPENAI_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY is not configured; skipping live test");

        StorybookProperties properties = new StorybookProperties();
        String liveModel = LiveTestCredentials.get("STORYBOOK_LIVE_MODEL");
        if (liveModel == null || liveModel.isBlank()) {
            liveModel = "gpt-4o-mini";
        }
        properties.getLlm().setModel(liveModel);

        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiLlmGateway gateway = new OpenAiLlmGateway(openAiApi, properties, new ObjectMapper());

        String systemPrompt = """
                You are an award-winning children's book architect.
                Generate a story blueprint for a 10-page children's picture book in Arabic.
                Output ONLY valid JSON matching this schema:
                {
                  "titleConcept": "string in Arabic",
                  "premise": "string in Arabic",
                  "beats": [
                    {
                      "pageNumber": 1,
                      "beat": "string in Arabic",
                      "emotionalArc": "string",
                      "sceneSetting": "string in Arabic",
                      "characters": ["string"]
                    }
                  ]
                }
                """;

        String userPrompt = "Child: سامي, age 6, Setting: بيروت, Theme: الشجاعة والصداقة, Page count: 10";

        LlmRequest<com.doova.ktab.features.storybook.story.StoryBlueprintResponse> request = new LlmRequest<>(
                LlmPurpose.STORY_BLUEPRINT,
                systemPrompt,
                userPrompt,
                List.of(),
                com.doova.ktab.features.storybook.story.StoryBlueprintResponse.class,
                2000
        );

        LlmCall<com.doova.ktab.features.storybook.story.StoryBlueprintResponse> response = gateway.call(request);

        assertThat(response).isNotNull();
        assertThat(response.value()).isNotNull();
        com.doova.ktab.features.storybook.story.StoryBlueprintResponse blueprint = response.value();
        assertThat(blueprint.titleConcept()).isNotBlank();
        assertThat(blueprint.premise()).isNotBlank();
        assertThat(blueprint.beats()).isNotEmpty();

        System.out.printf(
                "%n=== LIVE ARABIC BLUEPRINT GENERATION SUCCESSFUL ===%nTitle: %s%nPremise: %s%nTotal beats: %d%nFirst beat (Page %d): %s [Mood: %s]%n==================================================%n",
                blueprint.titleConcept(),
                blueprint.premise(),
                blueprint.beats().size(),
                blueprint.beats().get(0).pageNumber(),
                blueprint.beats().get(0).beat(),
                blueprint.beats().get(0).emotionalArc()
        );
    }
}
