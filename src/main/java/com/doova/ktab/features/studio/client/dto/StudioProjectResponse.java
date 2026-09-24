package com.doova.ktab.features.studio.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
        @JsonProperty("project_id") @JsonAlias({"projectId", "id"}) String projectId,
        String name,
        @JsonProperty("default_model_id") @JsonAlias({"defaultModelId"}) String defaultModelId,
        @JsonProperty("default_title_voice_id") @JsonAlias({"defaultTitleVoiceId", "default_title_voice_ref_id", "defaultTitleVoiceRefId"}) String defaultTitleVoiceId,
        @JsonProperty("default_paragraph_voice_id") @JsonAlias({"defaultParagraphVoiceId", "default_paragraph_voice_ref_id", "defaultParagraphVoiceRefId"}) String defaultParagraphVoiceId,
        @JsonProperty("quality_preset") @JsonAlias({"qualityPreset"}) String qualityPreset,
        String state,
        @JsonProperty("last_conversion_date_unix") @JsonAlias({"lastConversionDateUnix"}) Long lastConversionDateUnix
) {
}
