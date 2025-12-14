package com.doova.ktab.api.dto.response;

import java.time.LocalDateTime;

public record ReviewResponseDto(Long id, int rating, String comment, Long userId, String userName,
                                LocalDateTime createdAt) {
}
