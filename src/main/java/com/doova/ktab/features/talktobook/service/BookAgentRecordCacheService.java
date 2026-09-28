package com.doova.ktab.features.talktobook.service;

import com.doova.ktab.features.talktobook.dto.response.BookCitation;
import com.doova.ktab.features.talktobook.model.BookAgentRecord;
import com.doova.ktab.model.book.Book;

import java.util.List;
import java.util.Optional;

public interface BookAgentRecordCacheService {

    /**
     * Attempts exact SHA-256 hash match, followed by cosine similarity matching across cached book records.
     * Only fresh records can match. If matched, automatically increments the record's usage count and updates access timestamp.
     *
     * @param bookId            The book ID
     * @param questionHash      SHA-256 hash of normalized question
     * @param questionEmbedding Optional vector embedding for semantic comparison
     * @return Optional containing the cached record if found
     */
    Optional<BookAgentRecord> findSimilar(Long bookId, String questionHash, List<Float> questionEmbedding, String revision);

    /**
     * Inserts or refreshes an answered question with citations and triggers asynchronous storage check.
     */
    BookAgentRecord saveRecord(Book book, String question, String questionHash, List<Float> questionEmbedding,
                               String answer, List<BookCitation> citations, List<Integer> citedPages, boolean isWebAugmented, String revision);

    /** Capture before retrieval; persist this same revision after generation. */
    String computeRevision(Book book);

    /**
     * Computes the normalized SHA-256 hash of a question string.
     */
    String computeHash(String question);
}
