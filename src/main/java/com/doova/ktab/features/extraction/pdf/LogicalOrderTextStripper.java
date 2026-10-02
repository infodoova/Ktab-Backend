package com.doova.ktab.features.extraction.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PDFBox reverses the characters of each word correctly but never reorders the <em>phrases</em> of a line: it keeps stream
 * order or left-to-right position order. Publisher PDFs store a justified Arabic line as separate phrases written in visual
 * left-to-right order, so the sentence came out backwards (found on a real 800-page book). This stripper collects every
 * phrase with where it sits on the page and puts the phrases of an Arabic line back in right-to-left reading order.
 * Lines that are not Arabic, and single-phrase lines, are left exactly as PDFBox gives them.
 */
final class LogicalOrderTextStripper extends PDFTextStripper {

    private record Chunk(String text, float left, float right, boolean bold) {
        float center() {
            return (left + right) / 2f;
        }
    }

    private final List<List<Chunk>> lines = new ArrayList<>();
    private List<Chunk> current = new ArrayList<>();

    LogicalOrderTextStripper() throws IOException {
        setSortByPosition(true);
        setLineSeparator("\n");
    }

    /** The reconstructed lines of one page (1-based), in reading order. */
    List<String> pageLines(PDDocument doc, int pageNumber) throws IOException {
        lines.clear();
        current = new ArrayList<>();
        setStartPage(pageNumber);
        setEndPage(pageNumber);
        getText(doc); // drives the callbacks below; the text PDFBox itself produces is ignored
        endLine();
        List<String> out = new ArrayList<>();
        for (List<Chunk> line : lines) {
            out.add(join(line, false));
        }
        return out;
    }

    /** Reconstructed lines with bold lines wrapped in markdown **...** for structural TOC analysis. */
    List<String> pageLinesFormatted(PDDocument doc, int pageNumber) throws IOException {
        lines.clear();
        current = new ArrayList<>();
        setStartPage(pageNumber);
        setEndPage(pageNumber);
        getText(doc);
        endLine();
        List<String> out = new ArrayList<>();
        for (List<Chunk> line : lines) {
            out.add(join(line, true));
        }
        return out;
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        if (text == null || text.isBlank() || positions.isEmpty()) {
            return;
        }
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        boolean isBold = false;
        for (TextPosition p : positions) {
            left = Math.min(left, p.getXDirAdj());
            right = Math.max(right, p.getXDirAdj() + p.getWidthDirAdj());
            String fontName = p.getFont() != null && p.getFont().getName() != null
                    ? p.getFont().getName().toLowerCase() : "";
            if (fontName.contains("bold") || fontName.contains("black")
                    || fontName.contains("heavy") || fontName.contains("semibold")) {
                isBold = true;
            }
        }
        current.add(new Chunk(text.strip(), left, right, isBold));
    }

    @Override
    protected void writeWordSeparator() {
        // join() puts the separators in
    }

    @Override
    protected void writeLineSeparator() {
        endLine();
    }

    private void endLine() {
        if (!current.isEmpty()) {
            lines.add(current);
        }
        current = new ArrayList<>();
    }

    private static String join(List<Chunk> line, boolean formatted) {
        List<Chunk> ordered = new ArrayList<>(line);
        if (line.size() > 1 && isRightToLeft(line)) {
            ordered.sort(Comparator.comparingDouble(Chunk::center).reversed());
        } else {
            ordered.sort(Comparator.comparingDouble(Chunk::center));
        }
        StringBuilder sb = new StringBuilder();
        boolean lineHasBold = false;
        for (Chunk c : ordered) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(c.text());
            if (c.bold() && c.text().chars().anyMatch(Character::isLetter)) {
                lineHasBold = true;
            }
        }
        String str = sb.toString().trim();
        if (formatted && lineHasBold && !str.isBlank()) {
            return "**" + str + "**";
        }
        return str;
    }

    /** Arabic letters outnumber Latin letters (a line of only digits or Latin text is read left to right). */
    private static boolean isRightToLeft(List<Chunk> line) {
        int arabic = 0;
        int latin = 0;
        for (Chunk c : line) {
            for (int i = 0; i < c.text().length(); i++) {
                char ch = c.text().charAt(i);
                if ((ch >= '\u0600' && ch <= '\u06FF') || (ch >= '\u0750' && ch <= '\u077F')
                        || (ch >= '\u08A0' && ch <= '\u08FF') || (ch >= '\uFB50' && ch <= '\uFDFF')
                        || (ch >= '\uFE70' && ch <= '\uFEFF')) {
                    arabic++;
                } else if (Character.isLetter(ch)) {
                    latin++;
                }
            }
        }
        return arabic > 0 && arabic >= latin;
    }
}
