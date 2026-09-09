package com.doova.ktab.event.model;

public record BookPublishedEvent(Long bookId, String pdfKey) {
}
