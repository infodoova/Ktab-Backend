package com.doova.ktab.features.talktobook.dto;

import java.util.List;
import java.util.Map;
import com.doova.ktab.features.talktobook.dto.response.TalkToBookResponse.PageExcerpt;

/**
 * Retrieved context bundle from the book knowledge retrieval pipeline.
 *
 * @param contextText   Full concatenated text passed as context to the AI model
 * @param citedPages    Ordered list of col_page_number values referenced in this context
 * @param excerpts      Map of col_page_number → PageExcerpt for inline tooltip display in the client
 */
public record RetrievedContext(
        String contextText,
        List<Integer> citedPages,
        Map<Integer, PageExcerpt> excerpts
) {
    /**
     * Convenience constructor for cases where excerpts are not needed (e.g. macro with no pages).
     */
    public RetrievedContext(String contextText, List<Integer> citedPages) {
        this(contextText, citedPages, Map.of());
    }
}
