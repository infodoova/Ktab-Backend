package com.doova.ktab.features.extraction.dto;

/** Whatever the PDF itself declares; any field may be null and none of it is trusted blindly. */
public record BookMetadata(String title, String author, String subject, String keywords, String language, int pageCount) {
}
