package com.doova.ktab.dto.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BookStructureUpdateRequestDto(
        @NotEmpty @Valid List<BookSectionUpdateDto> sections
) {}
