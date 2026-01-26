package com.doova.ktab.dto.event;

public record BookPublishedEvent(Long bookId, String pdfKey) {
}