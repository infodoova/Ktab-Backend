package com.doova.ktab.features.talktobook.service;

import com.doova.ktab.model.book.Book;

import java.util.Optional;

public interface BookWebSearchService {

    /**
     * Executes an external web search to find established public summaries and character profiles for a book.
     *
     * @param book The book being analyzed
     * @return Optional containing the raw search snippet text, or empty if search fails/disabled
     */
    Optional<String> searchBookOverview(Book book);
}
