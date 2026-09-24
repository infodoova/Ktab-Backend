package com.doova.ktab.features.studio.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response shape for create/get project. Field names follow the schema the pipeline was
 * originally specified against; <strong>verify against the live ElevenLabs Studio API
 * reference before relying on this in production</strong> — this client was written
 * without network access to re-check the current contract. See {@link com.doova.ktab.features.studio.client.ElevenLabsStudioClient}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudioProjectResponse(
        @JsonProperty("project_id") String projectId,
        String name,
        @JsonProperty("default_model_id") String defaultModelId,
        @JsonProperty("default_title_voice_id") String defaultTitleVoiceId,
        @JsonProperty("default_paragraph_voice_id") String defaultParagraphVoiceId,
        @JsonProperty("quality_preset") String qualityPreset,
        String state,
        @JsonProperty("last_conversion_date_unix") Long lastConversionDateUnix
) {
}
