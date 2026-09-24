package com.doova.ktab.service.publisher;

import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.book.BookSourceFileResponseDto;
import com.doova.ktab.dto.book.PublisherReviewSearchRequest;
import com.doova.ktab.dto.publisher.ReviewDecisionRequest;
import com.doova.ktab.enums.book.BookSource;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.BookPublicationService;
import com.doova.ktab.service.book.BookResponseBuilderService;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import com.doova.ktab.service.publisher.impl.PublisherReviewServiceImpl;
import com.doova.ktab.utils.pagination.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublisherReviewServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookResponseBuilderService responseBuilder;

    @Mock
    private BookPublicationService bookPublicationService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PublisherReviewServiceImpl publisherReviewService;

    private Book testBook;
    private User testPublisher;
    private BookResponseDto testResponseDto;

    @BeforeEach
    void setUp() {
        testPublisher = new User();
        testPublisher.setId(50L);
        testPublisher.setEmail("publisher@ktab.com");

        testBook = new Book();
        testBook.setId(100L);
        testBook.setTitle("Under Review Novel");
        testBook.setStatus(BookStatus.UNDER_REVIEW);
        testBook.setSubmittedAt(Instant.now().minusSeconds(3600));

        testResponseDto = new BookResponseDto();
        testResponseDto.setId(100L);
        testResponseDto.setTitle("Under Review Novel");
        testResponseDto.setStatus(BookStatus.UNDER_REVIEW);
    }

    @Test
    @DisplayName("getReviewQueue_validPageAndSize_returnsPagedDtos")
    void getReviewQueue_validPageAndSize_returnsPagedDtos() {
        when(bookRepository.findAllByStatusAndBookSource(eq(BookStatus.UNDER_REVIEW), eq(BookSource.AUTHOR), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testBook)));
        when(responseBuilder.build(eq(testBook), eq(true))).thenReturn(testResponseDto);

        PageResponse<BookResponseDto> response = publisherReviewService.getReviewQueue(0, 10);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("searchReviewQueue_validRequest_returnsPagedDtos")
    void searchReviewQueue_validRequest_returnsPagedDtos() {
        PublisherReviewSearchRequest req = new PublisherReviewSearchRequest(
                "Novel", BookStatus.UNDER_REVIEW, null, null, null, null, null, null, 0, 10, "submittedAt", null
        );

        when(bookRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testBook)));
        when(responseBuilder.build(eq(testBook), eq(true))).thenReturn(testResponseDto);

        PageResponse<BookResponseDto> response = publisherReviewService.searchReviewQueue(req);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getBookById_existingBook_returnsDto")
    void getBookById_existingBook_returnsDto() {
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(responseBuilder.build(eq(testBook), eq(true))).thenReturn(testResponseDto);

        BookResponseDto result = publisherReviewService.getBookById(100L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getBookById_nonExistentBook_throwsResourceNotFoundException")
    void getBookById_nonExistentBook_throwsResourceNotFoundException() {
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publisherReviewService.getBookById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getMessageKey()).isEqualTo(ApiMessageKey.PUBLISHER_BOOK_NOT_FOUND));
    }

    @Test
    @DisplayName("getSourceFileForPublisher_validPdf_returnsEphemeralDownloadDto")
    void getSourceFileForPublisher_validPdf_returnsEphemeralDownloadDto() {
        Attachment attachment = new Attachment();
        attachment.setFileName("source.pdf");
        attachment.setStoragePath("books/source.pdf");

        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(attachmentService.getAttachment(100L, "Book", "PDF_SOURCE")).thenReturn(Optional.of(attachment));
        when(fileStorageService.getPreSignedDownloadUrl(eq("books/source.pdf"), any(Duration.class), eq("source.pdf")))
                .thenReturn("https://storage.ktab.com/signed-url");

        BookSourceFileResponseDto result = publisherReviewService.getSourceFileForPublisher(100L, testPublisher);

        assertThat(result).isNotNull();
        assertThat(result.bookId()).isEqualTo(100L);
        assertThat(result.fileName()).isEqualTo("source.pdf");
        assertThat(result.downloadUrl()).isEqualTo("https://storage.ktab.com/signed-url");
    }

    @Test
    @DisplayName("getSourceFileForPublisher_missingPdf_throwsResourceNotFoundException")
    void getSourceFileForPublisher_missingPdf_throwsResourceNotFoundException() {
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(attachmentService.getAttachment(100L, "Book", "PDF_SOURCE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publisherReviewService.getSourceFileForPublisher(100L, testPublisher))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getMessageKey()).isEqualTo(ApiMessageKey.BOOK_OCR_PDF_MISSING));
    }

    @Test
    @DisplayName("approveBook_underReviewBook_publishesBookAndReturnsDto")
    void approveBook_underReviewBook_publishesBookAndReturnsDto() {
        ReviewDecisionRequest req = new ReviewDecisionRequest("Great quality");
        Book publishedBook = new Book();
        publishedBook.setId(100L);
        publishedBook.setStatus(BookStatus.PUBLISHED);

        BookResponseDto publishedDto = new BookResponseDto();
        publishedDto.setId(100L);
        publishedDto.setStatus(BookStatus.PUBLISHED);

        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(bookPublicationService.publish(eq(testBook), eq(testPublisher), eq("Great quality"))).thenReturn(publishedBook);
        when(responseBuilder.build(eq(publishedBook), eq(true))).thenReturn(publishedDto);

        BookResponseDto result = publisherReviewService.approveBook(100L, req, testPublisher);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(BookStatus.PUBLISHED);
        verify(bookPublicationService).publish(testBook, testPublisher, "Great quality");
    }

    @Test
    @DisplayName("rejectBook_validNote_transitionsToDraftAndSaves")
    void rejectBook_validNote_transitionsToDraftAndSaves() {
        ReviewDecisionRequest req = new ReviewDecisionRequest("Please improve chapter 2 formatting.");

        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookResponseDto draftDto = new BookResponseDto();
        draftDto.setId(100L);
        draftDto.setStatus(BookStatus.DRAFT);
        when(responseBuilder.build(any(Book.class), eq(true))).thenReturn(draftDto);

        BookResponseDto result = publisherReviewService.rejectBook(100L, req, testPublisher);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(BookStatus.DRAFT);
        assertThat(testBook.getStatus()).isEqualTo(BookStatus.DRAFT);
        assertThat(testBook.getReviewedBy()).isEqualTo(testPublisher);
        assertThat(testBook.getReviewNote()).isEqualTo("Please improve chapter 2 formatting.");
        verify(bookRepository).save(testBook);
        verify(eventPublisher).publishEvent(any(com.doova.ktab.event.model.BookRejectedEvent.class));
    }

    @Test
    @DisplayName("rejectBook_blankNote_throwsBadRequestException")
    void rejectBook_blankNote_throwsBadRequestException() {
        ReviewDecisionRequest req = new ReviewDecisionRequest("   ");

        assertThatThrownBy(() -> publisherReviewService.rejectBook(100L, req, testPublisher))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getMessageKey()).isEqualTo(ApiMessageKey.PUBLISHER_REVIEW_NOTE_REQUIRED));

        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejectBook_nullRequest_throwsBadRequestException")
    void rejectBook_nullRequest_throwsBadRequestException() {
        assertThatThrownBy(() -> publisherReviewService.rejectBook(100L, null, testPublisher))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getMessageKey()).isEqualTo(ApiMessageKey.PUBLISHER_REVIEW_NOTE_REQUIRED));

        verify(bookRepository, never()).save(any());
    }
}
