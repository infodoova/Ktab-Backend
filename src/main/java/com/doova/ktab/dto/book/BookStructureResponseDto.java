package com.doova.ktab.dto.book;

import com.doova.ktab.enums.book.PaginationMode;
import com.doova.ktab.enums.book.ReadingDirection;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.book.StructureStatus;

import java.util.List;

public record BookStructureResponseDto(
        Long bookId,
        StructureStatus structureStatus,
        StructureSource structureSource,
        PaginationMode paginationMode,
        ReadingDirection readingDirection,
        List<BookSectionItemDto> sections
) {}
