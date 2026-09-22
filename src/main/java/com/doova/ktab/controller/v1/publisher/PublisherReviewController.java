package com.doova.ktab.controller.v1.publisher;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.book.BookSourceFileResponseDto;
import com.doova.ktab.dto.book.PublisherReviewSearchRequest;
import com.doova.ktab.dto.publisher.ReviewDecisionRequest;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.publisher.PublisherReviewService;
import com.doova.ktab.utils.pagination.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/publishers", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('PUBLISHER')")
@Tag(name = "Publisher Editorial Review API", description = "Endpoints for publishers and administrators to review, approve, and reject submitted books.")
public class PublisherReviewController {

    private final PublisherReviewService publisherReviewService;
    private final MessageSource messageSource;

    @Operation(summary = "Get books in the review queue ordered by submission time")
    @GetMapping("/review-queue")
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getReviewQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<BookResponseDto> queue = publisherReviewService.getReviewQueue(page, size);
        return ResponseUtils.success(queue,
                ApiMessageKey.PUBLISHER_REVIEW_QUEUE_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Search review queue with multi-facet filters")
    @PostMapping("/review-queue/search")
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> searchReviewQueue(
            @Valid @RequestBody PublisherReviewSearchRequest req) {
        PageResponse<BookResponseDto> result = publisherReviewService.searchReviewQueue(req);
        return ResponseUtils.success(result,
                ApiMessageKey.PUBLISHER_REVIEW_QUEUE_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get book full details for editorial review")
    @GetMapping("/books/{id}")
    public ResponseEntity<ApiResponse<BookResponseDto>> getBookById(
            @PathVariable Long id) {
        BookResponseDto book = publisherReviewService.getBookById(id);
        return ResponseUtils.success(book, ApiMessageKey.PUBLISHER_BOOK_FETCH_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }

    @Operation(summary = "Get secure ephemeral download URL for book source PDF")
    @GetMapping("/books/{id}/source-file")
    public ResponseEntity<ApiResponse<BookSourceFileResponseDto>> getSourceFile(
            @PathVariable Long id,
            @CurrentUser User publisher) {
        BookSourceFileResponseDto response = publisherReviewService.getSourceFileForPublisher(id, publisher);
        return ResponseUtils.success(response, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }

    @Operation(summary = "Approve a submitted book for public release")
    @PostMapping("/books/{id}/approve")
    public ResponseEntity<ApiResponse<BookResponseDto>> approveBook(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewDecisionRequest req,
            @CurrentUser User publisher) {
        BookResponseDto approved = publisherReviewService.approveBook(id, req, publisher);
        return ResponseUtils.success(approved, ApiMessageKey.PUBLISHER_BOOK_APPROVED_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }

    @Operation(summary = "Reject a submitted book with an editorial note and return to draft")
    @PostMapping("/books/{id}/reject")
    public ResponseEntity<ApiResponse<BookResponseDto>> rejectBook(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewDecisionRequest req,
            @CurrentUser User publisher) {
        BookResponseDto rejected = publisherReviewService.rejectBook(id, req, publisher);
        return ResponseUtils.success(rejected, ApiMessageKey.PUBLISHER_BOOK_REJECTED_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }
}
