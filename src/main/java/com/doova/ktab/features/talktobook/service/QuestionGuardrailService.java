package com.doova.ktab.features.talktobook.service;

import com.doova.ktab.features.talktobook.dto.GuardrailDecision;
import com.doova.ktab.model.book.Book;

/**
 * Enforces strict relevance to the target Book entity and validates against malicious or off-topic prompts.
 */
public interface QuestionGuardrailService {

    /**
     * Evaluates whether a question is strictly relevant to the book and safe to process.
     *
     * @param book     The active book entity
     * @param question The user's question
     * @return GuardrailDecision indicating if permitted, query intent, or refusal reason
     */
    GuardrailDecision evaluate(Book book, String question);
}
