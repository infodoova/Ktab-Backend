package com.doova.ktab.service.book.impl;

import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.mappers.book.BookMapper;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.book.BookResponseBuilderService;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookResponseBuilderServiceImpl implements BookResponseBuilderService {

    private final BookMapper bookMapper;
    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;

    @Override
    public BookResponseDto build(Book book) {
        BookResponseDto dto = bookMapper.toResponseDto(book);

        dto.setMainGenreId(book.getMainGenre().getId());
        dto.setMainGenreName(book.getMainGenre().getNameAr());

        dto.setSubGenreId(book.getSubGenre().getId());
        dto.setSubGenreName(book.getSubGenre().getNameAr());

        dto.setBookSource(book.getBookSource());
        dto.setCustomAuthorName(book.getCustomAuthorName());

        if (book.getCustomAuthorName() != null && !book.getCustomAuthorName().isBlank()) {
            dto.setAuthorName(book.getCustomAuthorName());
        } else if (book.getAuthor() != null) {
            dto.setAuthorName(book.getAuthor().getFullName());
        }

        if (book.getLibraryOrganization() != null) {
            dto.setLibraryOrganizationId(book.getLibraryOrganization().getId());
            dto.setLibraryOrganizationName(book.getLibraryOrganization().getName());
        }

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
