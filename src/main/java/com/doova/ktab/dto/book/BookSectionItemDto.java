package com.doova.ktab.dto.book;

import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;

import java.math.BigDecimal;
import java.util.List;

public record BookSectionItemDto(
        Long id,
        Long parentId,
        SectionType sectionType,
        int level,
        int sortOrder,
        String divisionLabel,
        Integer ordinal,
        String title,
        String printedStartLabel,
        Integer startPage,
        Integer endPage,
        String startAnchor,
        StructureSource source,
        BigDecimal confidence,
        boolean needsReview,
        List<BookSectionItemDto> children
) {}
