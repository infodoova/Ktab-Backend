package com.doova.ktab.features.talktobook.service;

import com.doova.ktab.model.book.Book;

public interface BookIdentityVerifierService {

    /**
     * Verifies that external web search results accurately represent this specific book,
     * preventing confusion with other books sharing identical or similar titles.
     *
     * @param book       The internal book entity
     * @param webContent The external text retrieved from web search
     * @return true if the identity is reliably verified; false otherwise
     */
    boolean verifyBookIdentity(Book book, String webContent);
}
