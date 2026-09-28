package com.doova.ktab.features.talktobook.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Exact verbatim citation snippet linked to a reference tag [n] in the answer.
 * Used by the frontend reader to locate and highlight the text dynamically.
 * Page numbers are intentionally excluded to ensure dynamic pagination independence.
 *
 * @param id      1-based numeric identifier corresponding to [n] in the answer
 * @param snippet Exact verbatim text snippet (approx. 8–25 words) from the retrieved chunk
 */
@Schema(description = "Verbatim citation snippet from the book text")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookCitation(
        @Schema(description = "1-based citation identifier corresponding to reference tag [n] in the answer", example = "1")
        Integer id,

        @Schema(description = "Exact verbatim substring from the source context/chunk (8 to 25 words)", example = "شهادة سياسية من الداخل عن تجربة محمد جواد ظريف في إدارة السياسة الخارجية")
        String snippet
) {
    /**
     * Backward-compatible constructor that ignores legacy page parameter.
     */
    public BookCitation(Integer id, String snippet, Integer page) {
        this(id, snippet);
    }
}
