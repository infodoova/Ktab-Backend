package com.doova.ktab.features.ai.dto.request;

import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

public record ConclusionRequest(

        @NotBlank String type,

        @Min(50) @Max(500) int wordCount,

        @NotBlank String audience,

        @NotNull MultipartFile file

) {
}
