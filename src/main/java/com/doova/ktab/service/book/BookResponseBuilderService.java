package com.doova.ktab.service.book;

import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.model.book.Book;

public interface BookResponseBuilderService {

    String BOOK_ENTITY_TYPE = "Book";
    String COVER_IMAGE_TYPE = "COVER_IMAGE";
    String PDF_SOURCE_TYPE = "PDF_SOURCE";

    /**
     * Convert Book -> BookResponseDto and attach signed URLs
     */
    BookResponseDto build(Book book);
}