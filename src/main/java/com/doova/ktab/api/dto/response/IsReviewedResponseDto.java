package com.doova.ktab.api.dto.response;

public record IsReviewedResponseDto(boolean reviewed, Integer rating, String comment, Long reviewId) {
}

