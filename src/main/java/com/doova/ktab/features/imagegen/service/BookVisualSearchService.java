package com.doova.ktab.features.imagegen.service;

/**
 * Service to execute real-time Google Web Search grounding for a book entity,
 * retrieving live web insights regarding cover art style, visual atmosphere,
 * and key aesthetic motifs to guide the image generator.
 */
public interface BookVisualSearchService {

    /**
     * Searches Google for real-world visual lore, cover aesthetics, character details,
     * and world atmosphere for the given book.
     *
     * @param bookTitle  The book title to search
     * @param authorName Optional author name to narrow search precision
     * @return Concise visual summary of search findings, or null if disabled/unavailable
     */
    String searchBookVisualLore(String bookTitle, String authorName);
}
