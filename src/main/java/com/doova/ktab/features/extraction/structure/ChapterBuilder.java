package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.Chapter;
import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.dto.Section;
import com.doova.ktab.features.extraction.dto.TocEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Spec steps 14-16: each top-level entry becomes a chapter that keeps its pages, its text and its sections. */
@Component
public class ChapterBuilder {

    public List<Chapter> build(List<TocEntry> toc, List<PageContent> pages) {
        List<Chapter> chapters = new ArrayList<>();
        int index = 1;
        for (TocEntry entry : toc) {
            List<PageContent> range = pages.stream()
                    .filter(p -> p.pdfPage() >= entry.startPage() && p.pdfPage() <= entry.endPage()).toList();
            StringBuilder text = new StringBuilder();
            for (PageContent p : range) {
                if (!p.cleanedText().isBlank()) {
                    if (text.length() > 0) {
                        text.append("\n\n");
                    }
                    text.append(p.cleanedText());
                }
            }
            chapters.add(new Chapter(index++, entry.title(), entry.startPage(), entry.endPage(), entry.type(),
                    text.toString(), range, entry.children().stream().map(ChapterBuilder::toSection).toList()));
        }
        return chapters;
    }

    private static Section toSection(TocEntry e) {
        return new Section(e.title(), e.level(), e.startPage(), e.endPage(), e.type(),
                e.children().stream().map(ChapterBuilder::toSection).toList());
    }
}
