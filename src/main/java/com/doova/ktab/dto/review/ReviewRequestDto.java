package com.doova.ktab.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequestDto(
        @NotNull(message = "{validation.rating.required}")
        @Min(value = 1, message = "{validation.rating.min}")
        @Max(value = 5, message = "{validation.rating.max}")
        Integer rating,

        @Size(max = 1000, message = "{validation.review.comment.size}")
        String comment
) {
}
