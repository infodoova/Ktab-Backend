package com.doova.ktab.features.talktobook.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Response returned by the Talk-to-Book assistant")
@JsonPropertyOrder({
        "question",
        "answer",
        "citations",
        "cached",
        "source",
        "hitCount"
})
public record TalkToBookResponse(

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "The submitted user question")
        String question,

        @Schema(description = "The generated answer or safe refusal explanation")
        String answer,

        @Schema(description = "Array of citations with verbatim snippets")
        List<BookCitation> citations,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Legacy cited page numbers - omitted from active responses")
        List<Integer> citedPages,

        /**
         * Legacy field retained for backward compatibility. Omitted if null.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Map of page number to a short excerpt from that page for inline tooltip display (legacy)")
        Map<Integer, PageExcerpt> citedExcerpts,

        @Schema(description = "Whether this answer was served from the cache")
        boolean cached,

        @Schema(description = "Source origin: CACHED, BOOK_METADATA, INTERNAL_RAG, WEB_AUGMENTED, or REJECTED_OFF_TOPIC")
        String source,

        @Schema(description = "Total times this or semantically similar question has been asked")
        int hitCount
) {

    /**
     * Primary constructor for modern verbatim text snippet responses (zero page numbers).
     */
    public TalkToBookResponse(
            String question,
            String answer,
            List<BookCitation> citations,
            boolean cached,
            String source,
            int hitCount
    ) {
        this(question, answer, citations != null ? citations : List.of(), null, null, cached, source, hitCount);
    }

    /**
     * Backward-compatible constructor that intentionally suppresses citedPages.
     */
    public TalkToBookResponse(
            String question,
            String answer,
            List<BookCitation> citations,
            List<Integer> citedPages,
            boolean cached,
            String source,
            int hitCount
    ) {
        this(question, answer, citations != null ? citations : List.of(), null, null, cached, source, hitCount);
    }

    /**
     * Legacy constructor for backward compatibility.
     */
    public TalkToBookResponse(
            String question,
            String answer,
            List<Integer> citedPages,
            Map<Integer, PageExcerpt> citedExcerpts,
            boolean cached,
            String source,
            int hitCount
    ) {
        this(question, answer, List.of(), null, null, cached, source, hitCount);
    }

    /**
     * A short excerpt from a specific book page for citation tooltip display (legacy).
     */
    @Schema(description = "Short excerpt from a cited book page")
    public record PageExcerpt(
            int pageNumber,
            String printedLabel,
            String excerpt
    ) {}
}
