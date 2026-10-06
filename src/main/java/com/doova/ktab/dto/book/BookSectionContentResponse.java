package com.doova.ktab.dto.book;

public record BookSectionContentResponse(
        Long sectionId,
        Long bookId,
        String title,
        String sectionType,
        String content
) {
}
