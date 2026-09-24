package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;

import java.util.List;

public record StorybookDetail(Long id, StorybookStatus status, String titleAr, String childNameAr,
                              LanguageVariety variety, TashkeelLevel tashkeelLevel, int pageCount,
                              String dedication, List<PageView> pages, String characterSheetUrl,
                              String failureReason, int lookRegenerationsLeft, int pageRegenerationsLeft) {
}
