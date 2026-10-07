package com.doova.ktab.dto.book;

import java.util.List;

public record BookNavigatorResponse(
        Long bookId,
        String bookTitle,
        int totalSections,
        List<BookSectionNodeDto> sections
) {
}
