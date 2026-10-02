package com.doova.ktab.service.book.impl;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import com.doova.ktab.utils.validator.ImageValidator;
import com.doova.ktab.utils.validator.PdfValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookFileServiceImplTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ImageValidator imageValidator;

    @Mock
    private PdfValidator pdfValidator;

    private BookFileServiceImpl bookFileService;

    private Book testBook;
    private MockMultipartFile mockPdf;

    @BeforeEach
    void setUp() {
        bookFileService = new BookFileServiceImpl(
                fileStorageService,
                attachmentService,
                imageValidator,
                pdfValidator
        );

        User author = new User();
        author.setId(10L);

        testBook = new Book();
        testBook.setId(100L);
        testBook.setLanguage("ar");
        testBook.setAuthor(author);

        mockPdf = new MockMultipartFile(
                "pdf", "book.pdf", "application/pdf", "dummy pdf content".getBytes());
    }

    @Test
    @DisplayName("handleCreateFiles when PDF is not digital throws IllegalArgumentException and does not store file")
    void handleCreateFiles_whenPdfIsNotDigital_throwsIllegalArgumentExceptionAndDoesNotStoreFile() throws IOException {
        doThrow(new IllegalArgumentException(ApiMessageKey.PDF_NOT_DIGITAL.getKey()))
                .when(pdfValidator).validatePdf(mockPdf, "ar");

        assertThatThrownBy(() -> bookFileService.handleCreateFiles(testBook, null, mockPdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_NOT_DIGITAL.getKey());

        verify(fileStorageService, never()).storeFile(any(), any());
        verify(attachmentService, never()).save(any());
    }

    @Test
    @DisplayName("handleUpdateFiles when PDF is not digital throws IllegalArgumentException and does not store file")
    void handleUpdateFiles_whenPdfIsNotDigital_throwsIllegalArgumentExceptionAndDoesNotStoreFile() throws IOException {
        doThrow(new IllegalArgumentException(ApiMessageKey.PDF_NOT_DIGITAL.getKey()))
                .when(pdfValidator).validatePdf(mockPdf, "ar");

        assertThatThrownBy(() -> bookFileService.handleUpdateFiles(testBook, null, mockPdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ApiMessageKey.PDF_NOT_DIGITAL.getKey());

        verify(fileStorageService, never()).storeFile(any(), any());
    }

    @Test
    @DisplayName("handleCreateFiles when PDF is digital stores file and creates attachment")
    void handleCreateFiles_whenPdfIsDigital_storesPdfAndCreatesAttachment() throws Exception {
        when(fileStorageService.storeFile(eq(mockPdf), any()))
                .thenReturn("books/pdf/10/book.pdf");

        bookFileService.handleCreateFiles(testBook, null, mockPdf);

        verify(pdfValidator).validatePdf(mockPdf, "ar");
        verify(fileStorageService).storeFile(eq(mockPdf), eq("books/pdf/10"));
        verify(attachmentService).save(any(Attachment.class));
    }
}
