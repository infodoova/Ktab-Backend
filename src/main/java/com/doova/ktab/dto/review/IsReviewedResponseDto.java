package com.doova.ktab.dto.review;

public record IsReviewedResponseDto(boolean reviewed, Integer rating, String comment, Long reviewId) {
}
