package com.doova.ktab.service.helpers;

import com.doova.ktab.api.dto.response.BookResponseDto;
import com.doova.ktab.enums.UrlStrategy;
import com.doova.ktab.mappers.book.BookMapper;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.interfaces.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookResponseBuilderService {

    private final BookMapper bookMapper;
    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;

    // Shared constants
    public static final String BOOK_ENTITY_TYPE = "Book";
    public static final String COVER_IMAGE_TYPE = "COVER_IMAGE";
    public static final String PDF_SOURCE_TYPE = "PDF_SOURCE";

    /**
     * Convert Book -> BookResponseDto and attach signed URLs
     */
    public BookResponseDto build(Book book) {
        BookResponseDto dto = bookMapper.toResponseDto(book);

        dto.setMainGenreId(book.getMainGenre().getId());
        dto.setMainGenreName(book.getMainGenre().getNameAr());

        dto.setSubGenreId(book.getSubGenre().getId());
        dto.setSubGenreName(book.getSubGenre().getNameAr());

        // Load cover
        Optional<Attachment> cover = attachmentService.getAttachment(book.getId(), BOOK_ENTITY_TYPE, COVER_IMAGE_TYPE);
        cover.ifPresent(att -> dto.setCoverImageUrl(fileStorageService.getFileUrl(att.getStoragePath(), UrlStrategy.SIGNED)));

        // Load PDF
        Optional<Attachment> pdf = attachmentService.getAttachment(book.getId(), BOOK_ENTITY_TYPE, PDF_SOURCE_TYPE);
        pdf.ifPresent(att -> dto.setPdfDownloadUrl(fileStorageService.getFileUrl(att.getStoragePath(), UrlStrategy.SIGNED)));
        pdf.ifPresent(att -> dto.setPdfFileName(att.getFileName()));

        return dto;
    }
}