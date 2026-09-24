package com.doova.ktab.features.studio.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * List Snapshots response element. See {@link StudioProjectResponse} for the
 * field-name-accuracy caveat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudioSnapshotSummary(
        @JsonProperty("snapshot_id") String snapshotId,
        @JsonProperty("created_at_unix") Long createdAtUnix
) {
}
