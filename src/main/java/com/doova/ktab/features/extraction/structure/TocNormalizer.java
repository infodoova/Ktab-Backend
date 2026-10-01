package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.TocEntry;
import com.doova.ktab.features.extraction.dto.TocEntryType;
import com.doova.ktab.features.ocr.text.SectionClassifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Spec steps 12-13 and 14: one model for every source, a preserved hierarchy, and an end page for every entry. */
@Component
public class TocNormalizer {

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
        sorted.sort(Comparator.comparing(RawEntry::startPage)); // stable: ties keep document order

        List<Node> flat = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int previousLevel = 0;
        for (RawEntry e : sorted) {
            if (!seen.add(HeadingWords.norm(e.title()) + "@" + e.startPage())) {
                continue;
            }
            int level = Math.max(1, Math.min(e.level(), previousLevel + 1));
            flat.add(new Node(e.title(), level, e.startPage()));
            previousLevel = level;
        }
        for (int i = 0; i < flat.size(); i++) {
            Node n = flat.get(i);
            int end = pageCount;
            for (int j = i + 1; j < flat.size(); j++) {
                if (flat.get(j).level <= n.level) {
                    end = Math.max(n.start, flat.get(j).start - 1);
                    break;
                }
            }
            n.end = end;
            n.type = typeOf(n.title, n.level);
        }

        List<Node> roots = new ArrayList<>();
        List<Node> stack = new ArrayList<>();
        for (Node n : flat) {
            while (!stack.isEmpty() && stack.get(stack.size() - 1).level >= n.level) {
                stack.remove(stack.size() - 1);
            }
            if (stack.isEmpty()) {
                roots.add(n);
            } else {
                stack.get(stack.size() - 1).children.add(n);
            }
            stack.add(n);
        }
        return roots.stream().map(TocNormalizer::toEntry).toList();
    }

    private static TocEntry toEntry(Node n) {
        return new TocEntry(n.title, n.level, n.start, n.end, n.type, n.children.stream().map(TocNormalizer::toEntry).toList());
    }

    private static TocEntryType typeOf(String title, int level) {
        if (level == 2) {
            return TocEntryType.SECTION;
        }
        if (level >= 3) {
            return TocEntryType.SUBSECTION;
        }
        return switch (SectionClassifier.classify(title)) {
            case INTRODUCTION, PREFACE, FOREWORD -> TocEntryType.INTRODUCTION;
            case CONCLUSION -> TocEntryType.CONCLUSION;
            case APPENDIX, INDEX, BIBLIOGRAPHY, GLOSSARY, FRONT_MATTER, DEDICATION -> TocEntryType.OTHER;
            default -> TocEntryType.CHAPTER;
        };
    }
}
