package com.doova.ktab.features.talktobook.service;

import com.doova.ktab.features.talktobook.dto.RetrievedContext;

public interface BookKnowledgeRetrieverService {

    /**
     * Retrieves specific page chunks matching a pinpoint factual question.
     *
     * @param bookId   The ID of the target book
     * @param question The user's question
     * @return RetrievedContext containing the combined text and cited page numbers
     */
    RetrievedContext retrievePinpointContext(Long bookId, String question);

    /**
     * Retrieves high-level book structure, table of contents, and introductory pages for macro-level questions.
     *
     * @param bookId The ID of the target book
     * @return RetrievedContext containing structure outline and sample pages
     */
    RetrievedContext retrieveMacroContext(Long bookId);
}
