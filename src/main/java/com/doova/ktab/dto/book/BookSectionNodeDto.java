package com.doova.ktab.dto.book;

import java.util.List;

public record BookSectionNodeDto(
        Long id,
        String title,
        Integer readerPage,
        boolean isAppendix,
        List<BookSectionNodeDto> children
) {
}
