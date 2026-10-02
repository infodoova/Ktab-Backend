package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.features.extraction.dto.TocEntry;
import com.doova.ktab.features.extraction.dto.TocEntryType;
import com.doova.ktab.features.ocr.text.SectionClassifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spec steps 12-13 and 14: one model for every source, a preserved hierarchy,
 * and an end page for every entry.
 */
@Component
public class TocNormalizer {

    /**
     * Front-matter and back-matter types that should never act as structural
     * parents of CHAPTER or SECTION entries. A chapter appearing after an Introduction
     * is an independent division, not a child.
     */
    private static final Set<TocEntryType> NON_PARENT_TYPES = EnumSet.of(
            TocEntryType.INTRODUCTION,
            TocEntryType.CONCLUSION,
            TocEntryType.OTHER   // covers APPENDIX, BIBLIOGRAPHY, INDEX, DEDICATION
    );

    private static final class Node {
        final String title;
        int level;
        final int start;
        int end;
        TocEntryType type;
        final List<Node> children = new ArrayList<>();

        Node(String title, int level, int start) {
            this.title = title;
            this.level = level;
            this.start = start;
        }
    }

    public List<TocEntry> normalize(List<RawEntry> entries, int pageCount) {
        List<RawEntry> sorted = new ArrayList<>(entries);
        // Null-safe: entries from the heuristic parser may have a null startPage
        // when PageNumberResolver failed to map them. Put them at the end.
        sorted.sort(Comparator.comparing(RawEntry::startPage,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // Determine if the document has any explicit PART divisions (الباب / القسم / Part / Book)
        boolean hasParts = sorted.stream().anyMatch(e ->
                SectionClassifier.classify(e.title()) == SectionType.PART
        );

        // Check if there are content entries (non-front-matter) already at level 1
        boolean hasContentAtLevel1 = sorted.stream().anyMatch(e -> {
            TocEntryType type = typeOf(e.title(), e.level());
            return e.level() == 1 && !NON_PARENT_TYPES.contains(type);
        });

        // When a book does NOT have Parts and has no content entries at level 1,
        // any chapters/content classified at level >= 2 should be shifted up so chapters
        // are level 1 and subsections are level 2.
        boolean shouldPromoteChapters = !hasParts && !hasContentAtLevel1;

        List<Node> flat = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int previousLevel = 0;
        for (RawEntry e : sorted) {
            if (e.startPage() == null) {
                continue;
            }
            if (!seen.add(HeadingWords.norm(e.title()) + "@" + e.startPage())) {
                continue;
            }

            int rawLevel = e.level();
            TocEntryType preliminaryType = typeOf(e.title(), rawLevel);
            if (shouldPromoteChapters && !NON_PARENT_TYPES.contains(preliminaryType)) {
                rawLevel = Math.max(1, rawLevel - 1);
            }

            int level = Math.max(1, Math.min(rawLevel, previousLevel + 1));
            Node node = new Node(e.title(), level, e.startPage());
            node.type = typeOf(node.title, node.level);
            flat.add(node);
            previousLevel = level;
        }

        // Calculate end pages:
        // A section ends right before the next section that is at the same or higher structural level,
        // OR when a front-matter section encounters the first chapter/part/content.
        for (int i = 0; i < flat.size(); i++) {
            Node n = flat.get(i);
            int end = pageCount;
            for (int j = i + 1; j < flat.size(); j++) {
                Node next = flat.get(j);
                boolean isSameOrHigherLevel = next.level <= n.level;
                boolean frontMatterHitContent = NON_PARENT_TYPES.contains(n.type)
                        && (next.type == TocEntryType.CHAPTER || next.type == TocEntryType.SECTION);
                if (isSameOrHigherLevel || frontMatterHitContent) {
                    end = Math.max(n.start, next.start - 1);
                    break;
                }
            }
            n.end = end;
        }

        // Build tree hierarchy:
        List<Node> roots = new ArrayList<>();
        List<Node> stack = new ArrayList<>();
        for (Node n : flat) {
            // Pop entries that are at the same or deeper level than the current node
            while (!stack.isEmpty() && stack.get(stack.size() - 1).level >= n.level) {
                stack.remove(stack.size() - 1);
            }
            // Front/back-matter entries should never parent content chapters or sections
            while (!stack.isEmpty() && NON_PARENT_TYPES.contains(stack.get(stack.size() - 1).type)
                    && (n.type == TocEntryType.CHAPTER || n.type == TocEntryType.SECTION)) {
                stack.remove(stack.size() - 1);
            }
            if (stack.isEmpty()) {
                roots.add(n);
            } else {
                stack.get(stack.size() - 1).children.add(n);
            }
            stack.add(n);
        }

        // Convert to TocEntry, guaranteeing that roots are level 1,
        // direct children are level 2, and grandchildren are level 3.
        return roots.stream().map(r -> toEntry(r, 1)).toList();
    }

    private static TocEntry toEntry(Node n, int effectiveLevel) {
        TocEntryType type = typeOf(n.title, effectiveLevel);
        return new TocEntry(n.title, effectiveLevel, n.start, n.end, type,
                n.children.stream().map(c -> toEntry(c, Math.min(3, effectiveLevel + 1))).toList());
    }

    private static TocEntryType typeOf(String title, int level) {
        SectionType st = SectionClassifier.classify(title);
        return switch (st) {
            case INTRODUCTION, PREFACE, FOREWORD -> TocEntryType.INTRODUCTION;
            case CONCLUSION -> TocEntryType.CONCLUSION;
            case APPENDIX, INDEX, BIBLIOGRAPHY, GLOSSARY, FRONT_MATTER, DEDICATION -> TocEntryType.OTHER;
            case CHAPTER, PART -> TocEntryType.CHAPTER;
            case SUBSECTION -> (level >= 3) ? TocEntryType.SUBSECTION : TocEntryType.SECTION;
            default -> {
                if (level == 1) yield TocEntryType.CHAPTER;
                if (level == 2) yield TocEntryType.SECTION;
                yield TocEntryType.SUBSECTION;
            }
        };
    }
}
