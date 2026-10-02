package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.illustration.VisualQaResponse;
import com.doova.ktab.features.storybook.story.CharacterBibleResponse;
import com.doova.ktab.features.storybook.story.CriticResponse;
import com.doova.ktab.features.storybook.story.ModerationResponse;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.StoryBlueprintResponse;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.ResponseFormat;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenAI call must carry a strict JSON schema so the model cannot pick its own key names. Strict mode only accepts a
 * schema where every object forbids extra properties and lists all of its properties as required.
 */
class OpenAiStructuredOutputTest {

    private static final List<Class<?>> TYPES = List.of(CharacterBibleResponse.class, StoryBlueprintResponse.class,
            StoryPlanResponse.class, PagePlan.class, CriticResponse.class, VisualQaResponse.class, ModerationResponse.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everyResponseTypeGetsAStrictJsonSchemaNamedAfterIt() {
        for (Class<?> type : TYPES) {
            ResponseFormat format = OpenAiStructuredOutput.formatFor(type);

            assertThat(format.getType()).as(type.getSimpleName()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
            assertThat(format.getJsonSchema().getName()).isEqualTo(type.getSimpleName());
            assertThat(format.getJsonSchema().getStrict()).isTrue();
        }
    }

    @Test
    void everyObjectInEverySchemaIsClosedAndFullyRequired() throws Exception {
        for (Class<?> type : TYPES) {
            JsonNode schema = mapper.valueToTree(OpenAiStructuredOutput.formatFor(type).getJsonSchema().getSchema());
            List<String> problems = new ArrayList<>();
            check(schema, type.getSimpleName(), problems);
            assertThat(problems).as("schema of " + type.getSimpleName()).isEmpty();
        }
    }

    @Test
    void theSchemaNamesTheKeysTheCodeReads() throws Exception {
        JsonNode blueprint = mapper.valueToTree(OpenAiStructuredOutput.formatFor(StoryBlueprintResponse.class).getJsonSchema().getSchema());
        assertThat(blueprint.get("properties").fieldNames()).toIterable().contains("titleConcept", "premise", "beats");

        JsonNode plan = mapper.valueToTree(OpenAiStructuredOutput.formatFor(StoryPlanResponse.class).getJsonSchema().getSchema());
        assertThat(plan.get("properties").fieldNames()).toIterable().contains("titleAr", "coverSceneEn", "pages");
    }

    private static void check(JsonNode node, String path, List<String> problems) {
        if (node == null || !node.isObject()) {
            return;
        }
        JsonNode props = node.get("properties");
        if (props != null) {
            if (!node.path("additionalProperties").isBoolean() || node.get("additionalProperties").asBoolean()) {
                problems.add(path + " allows additional properties");
            }
            List<String> required = new ArrayList<>();
            node.path("required").forEach(r -> required.add(r.asText()));
            props.fieldNames().forEachRemaining(name -> {
                if (!required.contains(name)) {
                    problems.add(path + "." + name + " is not required");
                }
            });
        }
        node.fields().forEachRemaining(e -> {
            if (e.getValue().isObject()) {
                check(e.getValue(), path + "." + e.getKey(), problems);
            } else if (e.getValue().isArray()) {
                int i = 0;
                for (JsonNode item : e.getValue()) {
                    check(item, path + "." + e.getKey() + "[" + i++ + "]", problems);
                }
            }
        });
    }
}
