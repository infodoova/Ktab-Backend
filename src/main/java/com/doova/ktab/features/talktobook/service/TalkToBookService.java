package com.doova.ktab.features.talktobook.service;

import com.doova.ktab.features.talktobook.dto.request.TalkToBookRequest;
import com.doova.ktab.features.talktobook.dto.response.TalkToBookResponse;

public interface TalkToBookService {

    /**
     * Processes a user question about a specific book, adhering to strict book relevance guardrails,
     * semantic caching, hybrid RAG, and verified web augmentation.
     *
     * @param bookId  The target book identifier
     * @param request The user's question request payload
     * @param userId  The authenticated user ID
     * @return TalkToBookResponse containing the answer and citation metadata
     */
    TalkToBookResponse askQuestion(Long bookId, TalkToBookRequest request, Long userId);
}
