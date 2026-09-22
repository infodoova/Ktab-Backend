package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;
import com.doova.ktab.features.ocr.text.SectionClassifier;
import com.doova.ktab.model.book.BookPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class HeadingsStructureBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Builds sections directly from detected headings on pages when no TOC is present.
     */
    public List<TocAligner.AlignedSection> buildFromHeadings(List<BookPage> pages) {
        List<TocAligner.AlignedSection> result = new ArrayList<>();
        String lastRunningHeader = null;

        for (BookPage page : pages) {
            int pageNum = page.getPageNumber();

            // Detect running header transitions
            String header = page.getRunningHeader();
            if (header != null && !header.isBlank()) {
                String normHeader = ArabicTextNormalizer.normalize(header);
                if (lastRunningHeader != null && !normHeader.equals(lastRunningHeader)) {
                    // Chapter/part boundary transition
                    result.add(new TocAligner.AlignedSection(
                            header.trim(),
                            SectionClassifier.extractDivisionLabel(header).orElse(null),
                            SectionClassifier.extractOrdinal(header).orElse(null),
                            1,
                            page.getPrintedPageLabel(),
                            SectionClassifier.classify(header),
                            pageNum,
                            header.trim(),
                            BigDecimal.valueOf(0.75),
                            false
                    ));
                }
                lastRunningHeader = normHeader;
            }

            // Detect page headings
            if (page.getHeadings() != null && !page.getHeadings().isBlank()) {
                try {
                    List<Map<String, Object>> headings = objectMapper.readValue(page.getHeadings(), new TypeReference<>() {});
                    for (Map<String, Object> h : headings) {
                        String text = (String) h.get("text");
                        Number levelHint = (Number) h.get("levelHint");
                        if (text != null && !text.isBlank()) {
                            int lvl = levelHint != null ? levelHint.intValue() : 1;
                            result.add(new TocAligner.AlignedSection(
                                    text.trim(),
                                    SectionClassifier.extractDivisionLabel(text).orElse(null),
                                    SectionClassifier.extractOrdinal(text).orElse(null),
                                    lvl,
                                    page.getPrintedPageLabel(),
                                    SectionClassifier.classify(text),
                                    pageNum,
                                    text.trim(),
                                    BigDecimal.valueOf(0.80),
                                    false
                            ));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        return result;
    }
}
