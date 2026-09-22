package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.book.StructureStatus;
import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookSection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class SectionTreeBuilder {

    private final OcrProperties properties;

    public record TreeBuildResult(List<BookSection> sections, StructureStatus status) {}

    /**
     * Builds the hierarchical BookSection tree and calculates start/end page boundaries.
     */
    public TreeBuildResult build(
            Book book,
            List<TocAligner.AlignedSection> alignedSections,
            int totalPages,
            StructureSource source
    ) {
        if (alignedSections == null || alignedSections.isEmpty()) {
            return new TreeBuildResult(List.of(), StructureStatus.NEEDS_REVIEW);
        }

        List<BookSection> sections = new ArrayList<>();
        int sortOrder = 1;

        // 1. Front Matter section if content starts after page 1
        int firstStart = alignedSections.getFirst().startPage();
        if (firstStart > 1) {
            BookSection frontMatter = new BookSection();
            frontMatter.setBook(book);
            frontMatter.setSectionType(SectionType.FRONT_MATTER);
            frontMatter.setLevel(0);
            frontMatter.setSortOrder(sortOrder++);
            frontMatter.setTitle("المقدمة والصفحات التمهيدية");
            frontMatter.setTitleNormalized(ArabicTextNormalizer.normalize(frontMatter.getTitle()));
            frontMatter.setStartPage(1);
            frontMatter.setEndPage(firstStart - 1);
            frontMatter.setSource(source);
            frontMatter.setConfidence(BigDecimal.valueOf(1.0));
            frontMatter.setNeedsReview(false);
            sections.add(frontMatter);
        }

        // 2. Build sections and parent hierarchy using a level stack
        Deque<BookSection> stack = new ArrayDeque<>();

        for (int i = 0; i < alignedSections.size(); i++) {
            TocAligner.AlignedSection as = alignedSections.get(i);

            BookSection section = new BookSection();
            section.setBook(book);
            section.setSectionType(as.sectionType() != null ? as.sectionType() : SectionType.OTHER);
            section.setLevel(as.level());
            section.setSortOrder(sortOrder++);
            section.setDivisionLabel(as.divisionLabel());
            section.setOrdinal(as.ordinal());
            section.setTitle(as.title());
            section.setTitleNormalized(ArabicTextNormalizer.normalize(as.title()));
            section.setPrintedStartLabel(as.printedLabel());
            section.setStartPage(as.startPage());
            section.setStartAnchor(as.startAnchor());
            section.setSource(source);
            section.setConfidence(as.confidence());
            section.setNeedsReview(as.needsReview());

            // Compute endPage: next section's startPage - 1, or totalPages for last section
            int nextStart = (i + 1 < alignedSections.size())
                    ? alignedSections.get(i + 1).startPage()
                    : totalPages + 1;
            section.setEndPage(Math.max(as.startPage(), nextStart - 1));

            // Assign parent based on level hierarchy
            while (!stack.isEmpty() && stack.peek().getLevel() >= as.level()) {
                stack.pop();
            }

            if (!stack.isEmpty()) {
                section.setParent(stack.peek());
            }

            stack.push(section);
            sections.add(section);
        }

        // 3. Determine book structure status
        double reviewThreshold = properties.getStructure().getReviewConfidence();
        boolean anyNeedsReview = sections.stream()
                .anyMatch(s -> s.isNeedsReview() || s.getConfidence().doubleValue() < reviewThreshold);

        StructureStatus status = anyNeedsReview ? StructureStatus.NEEDS_REVIEW : StructureStatus.RESOLVED;

        log.info("Built section tree for bookId={} with {} sections. Status: {}",
                book.getId(), sections.size(), status);

        return new TreeBuildResult(sections, status);
    }
}
