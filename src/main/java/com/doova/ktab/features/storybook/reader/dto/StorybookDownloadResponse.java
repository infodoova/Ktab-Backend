package com.doova.ktab.features.storybook.reader.dto;

import java.time.Instant;

public record StorybookDownloadResponse(
        String downloadUrl,
        String filename,
        Instant expiresAt
) {
}
