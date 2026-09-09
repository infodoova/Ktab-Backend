package com.doova.ktab.dto.library;

import jakarta.validation.constraints.NotNull;

public record AssignBookRequest(
        @NotNull(message = "{validation.book.id.required}")
        Long bookId
) {
}
