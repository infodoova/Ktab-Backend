package com.doova.ktab.dto.book;

import com.doova.ktab.enums.book.SectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookSectionUpdateDto(
        Long id,
        Long parentId,
        @NotNull SectionType sectionType,
        int level,
        int sortOrder,
        String divisionLabel,
        Integer ordinal,
        @NotBlank String title,
        String printedStartLabel,
        @NotNull Integer startPage,
        Integer endPage,
        String startAnchor
) {}
