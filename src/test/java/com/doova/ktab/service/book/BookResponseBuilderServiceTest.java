package com.doova.ktab.service.book;

import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.mappers.book.BookMapper;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.service.book.impl.BookResponseBuilderServiceImpl;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookResponseBuilderServiceTest {

    @Mock
    private BookMapper bookMapper;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private BookResponseBuilderServiceImpl responseBuilder;

    private Book testBook;
    private BookResponseDto baseDto;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        testBook = new Book();
        testBook.setId(94L);
        testBook.setTitle("Confidential Book");

        baseDto = new BookResponseDto();
        baseDto.setId(94L);
        baseDto.setTitle("Confidential Book");

        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("build_defaultReaderMode_omitsPdfDownloadUrlAndFileName")
    void build_defaultReaderMode_omitsPdfDownloadUrlAndFileName() throws Exception {
        when(bookMapper.toResponseDto(testBook)).thenReturn(baseDto);

        Attachment coverAttachment = new Attachment();
        coverAttachment.setStoragePath("covers/cover.png");
        when(attachmentService.getAttachment(94L, "Book", "COVER_IMAGE"))
                .thenReturn(Optional.of(coverAttachment));
        when(fileStorageService.getFileUrl("covers/cover.png", UrlStrategy.SIGNED))
                .thenReturn("https://storage.ktab.com/cover.png");

        // Act - reader mode (default)
        BookResponseDto result = responseBuilder.build(testBook);

        // Assert
        assertNotNull(result);
        assertEquals("https://storage.ktab.com/cover.png", result.getCoverImageUrl());
        assertNull(result.getPdfDownloadUrl());
        assertNull(result.getPdfFileName());

        // Verify attachmentService never queried PDF attachment
        verify(attachmentService, never()).getAttachment(94L, "Book", "PDF_SOURCE");
        verify(fileStorageService, never()).getFileUrl(eq("books/book.pdf"), any());

        // Verify JSON serialization omits pdfDownloadUrl
        String json = objectMapper.writeValueAsString(result);
        assertFalse(json.contains("pdfDownloadUrl"), "JSON must not leak pdfDownloadUrl");
        assertFalse(json.contains("pdfFileName"), "JSON must not leak pdfFileName");
    }

    @Test
    @DisplayName("build_withIncludePdfUrlTrue_includesPdfDownloadUrlAndFileName")
    void build_withIncludePdfUrlTrue_includesPdfDownloadUrlAndFileName() throws Exception {
        when(bookMapper.toResponseDto(testBook)).thenReturn(baseDto);

        Attachment coverAttachment = new Attachment();
        coverAttachment.setStoragePath("covers/cover.png");
        when(attachmentService.getAttachment(94L, "Book", "COVER_IMAGE"))
                .thenReturn(Optional.of(coverAttachment));
        when(fileStorageService.getFileUrl("covers/cover.png", UrlStrategy.SIGNED))
                .thenReturn("https://storage.ktab.com/cover.png");

        Attachment pdfAttachment = new Attachment();
        pdfAttachment.setStoragePath("books/book.pdf");
        pdfAttachment.setFileName("book.pdf");
        when(attachmentService.getAttachment(94L, "Book", "PDF_SOURCE"))
                .thenReturn(Optional.of(pdfAttachment));
        when(fileStorageService.getFileUrl("books/book.pdf", UrlStrategy.SIGNED))
                .thenReturn("https://storage.ktab.com/confidential-signed-book.pdf");

        // Act - management mode (includePdfUrl = true)
        BookResponseDto result = responseBuilder.build(testBook, true);

        // Assert
        assertNotNull(result);
        assertEquals("https://storage.ktab.com/confidential-signed-book.pdf", result.getPdfDownloadUrl());
        assertEquals("book.pdf", result.getPdfFileName());

        // Verify JSON serialization includes pdfDownloadUrl when present
        String json = objectMapper.writeValueAsString(result);
        assertTrue(json.contains("pdfDownloadUrl"));
        assertTrue(json.contains("https://storage.ktab.com/confidential-signed-book.pdf"));
    }

    @Test
    @DisplayName("build_whenUserIsReaderEvenIfIncludePdfUrlIsTrue_omitsPdfDownloadUrl")
    void build_whenUserIsReaderEvenIfIncludePdfUrlIsTrue_omitsPdfDownloadUrl() throws Exception {
        // Arrange security context with READER authority
        org.springframework.security.core.Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "reader@ktab.com",
                "credentials",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("READER"))
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            when(bookMapper.toResponseDto(testBook)).thenReturn(baseDto);

            Attachment coverAttachment = new Attachment();
            coverAttachment.setStoragePath("covers/cover.png");
            when(attachmentService.getAttachment(94L, "Book", "COVER_IMAGE"))
                    .thenReturn(Optional.of(coverAttachment));
            when(fileStorageService.getFileUrl("covers/cover.png", UrlStrategy.SIGNED))
                    .thenReturn("https://storage.ktab.com/cover.png");

            // Act - even if someone calls build(testBook, true), Reader role MUST prevent PDF URL leakage
            BookResponseDto result = responseBuilder.build(testBook, true);

            // Assert
            assertNotNull(result);
            assertNull(result.getPdfDownloadUrl());
            assertNull(result.getPdfFileName());
            verify(attachmentService, never()).getAttachment(94L, "Book", "PDF_SOURCE");

            String json = objectMapper.writeValueAsString(result);
            assertFalse(json.contains("pdfDownloadUrl"), "JSON must never leak pdfDownloadUrl to a Reader");
            assertFalse(json.contains("pdfFileName"), "JSON must never leak pdfFileName to a Reader");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
