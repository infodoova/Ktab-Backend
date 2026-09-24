package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.enums.StorybookStatus;

import java.time.LocalDateTime;

public record StorybookSummary(Long id, String titleAr, String childNameAr, StorybookStatus status,
                               int pageCount, LocalDateTime createdAt) {
}
