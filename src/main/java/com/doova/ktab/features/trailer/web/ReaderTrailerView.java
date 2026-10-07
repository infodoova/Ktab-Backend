package com.doova.ktab.features.trailer.web;

/**
 * What a reader needs to play a book's trailer, and nothing else: no status, error or agent details. The links are
 * short-lived, so fetch them when the player opens.
 */
public record ReaderTrailerView(Long trailerId, String video, String videoClean, String captions, long expiresInSeconds) {
}
