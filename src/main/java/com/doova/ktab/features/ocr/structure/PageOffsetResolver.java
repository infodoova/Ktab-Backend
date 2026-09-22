package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.features.ocr.text.PageLabelParser;
import com.doova.ktab.model.book.BookPage;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Resolves printed page numbers to book page indices using piecewise constant runs
 * with majority-vote outlier rejection.
 */
@Slf4j
public class PageOffsetResolver {

    public record OffsetRun(int minPrinted, int maxPrinted, int offset) {}

    private final List<OffsetRun> runs = new ArrayList<>();
    private final Integer globalMedianOffset;

    public PageOffsetResolver(List<BookPage> pages) {
        List<Point> points = new ArrayList<>();

        for (BookPage page : pages) {
            // Ignore non-body pages (covers, blanks) when resolving offset
            if (page.getPageKind() == PageKind.COVER || page.getPageKind() == PageKind.BLANK) {
                continue;
            }

            Optional<Integer> printedOpt = PageLabelParser.parseNumeric(page.getPrintedPageLabel());
            if (printedOpt.isPresent()) {
                int printed = printedOpt.get();
                int bookPage = page.getPageNumber();
                int offset = bookPage - printed;
                points.add(new Point(bookPage, printed, offset));
            }
        }

        if (points.isEmpty()) {
            this.globalMedianOffset = null;
            return;
        }

        // Sort by printed page number
        points.sort(Comparator.comparingInt(p -> p.printed));

        // 1. Compute global median offset for fallback
        List<Integer> allOffsets = points.stream().map(p -> p.offset).sorted().toList();
        this.globalMedianOffset = allOffsets.get(allOffsets.size() / 2);

        // 2. Build smoothed points using a sliding window majority vote
        List<Point> smoothed = new ArrayList<>(points.size());
        int windowSize = 5;

        for (int i = 0; i < points.size(); i++) {
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(points.size(), i + windowSize / 2 + 1);

            Map<Integer, Integer> freq = new HashMap<>();
            for (int j = start; j < end; j++) {
                freq.merge(points.get(j).offset, 1, Integer::sum);
            }

            // Find majority offset in window
            int majorityOffset = points.get(i).offset;
            int maxCount = 0;
            for (Map.Entry<Integer, Integer> entry : freq.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    majorityOffset = entry.getKey();
                }
            }

            smoothed.add(new Point(points.get(i).bookPage, points.get(i).printed, majorityOffset));
        }

        // 3. Build piecewise constant runs
        if (!smoothed.isEmpty()) {
            int runStart = smoothed.getFirst().printed;
            int currentOffset = smoothed.getFirst().offset;
            int runEnd = smoothed.getFirst().printed;

            for (int i = 1; i < smoothed.size(); i++) {
                Point p = smoothed.get(i);
                if (p.offset == currentOffset) {
                    runEnd = p.printed;
                } else {
                    runs.add(new OffsetRun(runStart, runEnd, currentOffset));
                    runStart = p.printed;
                    runEnd = p.printed;
                    currentOffset = p.offset;
                }
            }
            runs.add(new OffsetRun(runStart, runEnd, currentOffset));
        }

        log.debug("Built {} offset runs from {} printed page labels. Median offset: {}",
                runs.size(), points.size(), globalMedianOffset);
    }

    /**
     * Resolves a printed page number into the actual book page index.
     */
    public Optional<Integer> toPageIndex(int printedNumber) {
        if (printedNumber <= 0 || runs.isEmpty()) {
            return Optional.empty();
        }

        // Check if falls within an existing run
        for (OffsetRun run : runs) {
            if (printedNumber >= run.minPrinted && printedNumber <= run.maxPrinted) {
                return Optional.of(printedNumber + run.offset);
            }
        }

        // Fall back to closest run or global median
        OffsetRun closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (OffsetRun run : runs) {
            int dist = Math.min(Math.abs(printedNumber - run.minPrinted), Math.abs(printedNumber - run.maxPrinted));
            if (dist < minDistance) {
                minDistance = dist;
                closest = run;
            }
        }

        if (closest != null) {
            return Optional.of(printedNumber + closest.offset);
        }

        return globalMedianOffset != null ? Optional.of(printedNumber + globalMedianOffset) : Optional.empty();
    }

    public List<OffsetRun> getRuns() {
        return Collections.unmodifiableList(runs);
    }

    private record Point(int bookPage, int printed, int offset) {}
}
