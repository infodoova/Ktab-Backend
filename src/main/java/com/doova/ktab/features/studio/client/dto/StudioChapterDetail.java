package com.doova.ktab.features.studio.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Get Chapter response — the Tier 2 content-sync payload (docs/ocr_engine_v3.md, Phase 3.7).
 * See {@link StudioProjectResponse} for the field-name-accuracy caveat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudioChapterDetail(
        @JsonProperty("chapter_id") String chapterId,
        String name,
        String state,
        Content content
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(List<Block> blocks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Block(
            @JsonProperty("block_id") String blockId,
            /** "p" | "h1" | "h2" | "h3" */
            String type,
            List<Node> nodes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Node(
            String type,
            String text,
            @JsonProperty("voice_id") String voiceId
    ) {
    }
}
