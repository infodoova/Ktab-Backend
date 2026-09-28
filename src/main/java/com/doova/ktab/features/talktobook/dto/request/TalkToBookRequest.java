package com.doova.ktab.features.talktobook.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for querying a book with AI")
public record TalkToBookRequest(

        @Schema(description = "The user question concerning the book", example = "Who is the main character and what is their mission?")
        @NotBlank(message = "{validation.talktobook.question.required}")
        @Size(min = 3, max = 350, message = "{validation.talktobook.question.size}")
        @Pattern(regexp = "^(?!.*(.)\\1{9,}).*$", message = "{validation.talktobook.question.spam}")
        String question
) {
    public TalkToBookRequest {
        if (question != null) {
            question = question.trim();
        }
    }
}
