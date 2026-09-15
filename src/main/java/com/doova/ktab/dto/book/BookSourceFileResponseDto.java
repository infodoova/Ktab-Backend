package com.doova.ktab.dto.book;

import java.time.Instant;

public record BookSourceFileResponseDto(
        Long bookId,
        String fileName,
        String downloadUrl,
        Instant expiresAt
) {}
