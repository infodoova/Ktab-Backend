package com.doova.ktab.features.storybook.llm;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.api.ResponseFormat;

/**
 * OpenAI Structured Outputs: the reply is forced to match a strict JSON schema generated from the response record, so the
 * model cannot invent its own key names (the cause of null blueprints, empty bibles and unreadable rewrites).
 */
public final class OpenAiStructuredOutput {

    private OpenAiStructuredOutput() {
    }

    public static ResponseFormat formatFor(Class<?> type) {
        String schema = new BeanOutputConverter<>(type).getJsonSchema();
        return ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(ResponseFormat.JsonSchema.builder()
                        .name(type.getSimpleName())
                        .schema(schema)
                        .strict(true)
                        .build())
                .build();
    }
}
