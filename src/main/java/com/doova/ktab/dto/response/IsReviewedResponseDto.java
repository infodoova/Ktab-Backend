package com.doova.ktab.dto.response;

public record IsReviewedResponseDto(boolean reviewed, Integer rating, String comment, Long reviewId) {
}

