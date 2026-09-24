package com.doova.ktab.features.studio.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * List Chapters response element — the Tier 1 status-sync payload (docs/ocr_engine_v3.md,
 * Phase 3.7). Deliberately excludes {@code content} — status sync must never touch it.
 * See {@link StudioProjectResponse} for the field-name-accuracy caveat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudioChapterSummary(
        @JsonProperty("chapter_id") @JsonAlias({"chapterId", "id"}) String chapterId,
        String name,
        String state,
        @JsonProperty("can_be_downloaded") boolean canBeDownloaded,
        @JsonProperty("last_conversion_date_unix") Long lastConversionDateUnix,
        @JsonProperty("conversion_progress") Double conversionProgress,
        @JsonProperty("last_conversion_error") String lastConversionError,
        @JsonProperty("characters_converted") Integer charactersConverted,
        @JsonProperty("characters_unconverted") Integer charactersUnconverted
) {
    public boolean hasError() {
        return lastConversionError != null && !lastConversionError.isBlank();
    }

    public boolean isFullyConverted() {
        return conversionProgress != null && conversionProgress >= 1.0;
    }
}
