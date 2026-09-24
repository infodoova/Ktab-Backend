package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.story.TashkeelFilter;
import com.doova.ktab.features.storybook.web.dto.PageView;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StorybookViewMapper {

    private final FileStorageService storage;
    private final StorybookProperties properties;

    public StorybookDetail toDetail(Storybook book, List<StorybookPage> pages, StorybookCharacter child) {
        List<PageView> views = pages.stream()
                .map(p -> new PageView(p.getPageIndex(), p.getKind(),
                        p.getTextAr() == null ? null : TashkeelFilter.apply(p.getTextAr(), book.getTashkeelLevel()),
                        p.getTextZone(),
                        p.getCurrentImage() == null ? null : storage.getFileUrl(p.getCurrentImage().getImageKey(), UrlStrategy.SIGNED)))
                .toList();
        String sheetUrl = child == null || child.getSheetKey() == null ? null
                : storage.getFileUrl(child.getSheetKey(), UrlStrategy.SIGNED);
        StorybookProperties.Limits limits = properties.getLimits();
        return new StorybookDetail(book.getId(), book.getStatus(),
                book.getTitleAr() == null ? null : TashkeelFilter.apply(book.getTitleAr(), book.getTashkeelLevel()),
                book.getInputs().childNameAr(), book.getVariety(), book.getTashkeelLevel(), book.getPageCount(),
                book.getDedication(), views, sheetUrl, book.getFailureReason(),
                Math.max(0, limits.getLookRegenerations() - book.getLookRegenerations()),
                Math.max(0, limits.getPageRegenerationsPerBook() - book.getPageRegenerations()));
    }
}
