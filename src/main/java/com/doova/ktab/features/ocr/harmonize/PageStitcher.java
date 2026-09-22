package com.doova.ktab.features.ocr.harmonize;

import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.model.book.BookPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PageStitcher {

    /**
     * Inspects consecutive pages to identify mid-sentence continuations.
     */
    public void evaluateStitching(List<BookPage> pages) {
        if (pages == null || pages.size() < 2) return;

        for (int i = 0; i < pages.size() - 1; i++) {
            BookPage curr = pages.get(i);
            BookPage next = pages.get(i + 1);

            if (curr.getPageKind() == PageKind.BODY && next.getPageKind() == PageKind.BODY) {
                boolean currEndsMid = Boolean.TRUE.equals(curr.getEndsMidSentence());
                boolean nextStartsMid = Boolean.TRUE.equals(next.getStartsMidSentence());

                if (currEndsMid && nextStartsMid) {
                    log.debug("Continuous sentence detected across pages {} -> {}",
                            curr.getPageNumber(), next.getPageNumber());
                }
            }
        }
    }
}
