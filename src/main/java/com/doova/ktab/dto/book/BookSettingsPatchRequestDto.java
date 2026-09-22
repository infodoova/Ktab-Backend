package com.doova.ktab.dto.book;

import com.doova.ktab.enums.book.ReadingDirection;

public record BookSettingsPatchRequestDto(
        ReadingDirection readingDirection
) {}
