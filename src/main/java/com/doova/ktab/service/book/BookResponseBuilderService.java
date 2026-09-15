package com.doova.ktab.service.book;

import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.model.book.Book;

public interface BookResponseBuilderService {

    String BOOK_ENTITY_TYPE = "Book";
    String COVER_IMAGE_TYPE = "COVER_IMAGE";
    String PDF_SOURCE_TYPE = "PDF_SOURCE";

    /**
     * Convert Book -> BookResponseDto without confidential PDF download URL (safe for readers/public)
     */
    BookResponseDto build(Book book);

    /**
     * Convert Book -> BookResponseDto with explicit control over including confidential PDF download URL
     */
    BookResponseDto build(Book book, boolean includePdfUrl);
}