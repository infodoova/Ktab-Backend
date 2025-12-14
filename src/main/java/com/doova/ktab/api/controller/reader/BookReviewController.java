package com.doova.ktab.api.controller.reader;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.annotation.UserMatchesOrAdmin;
import com.doova.ktab.api.dto.request.ReviewRequestDto;
import com.doova.ktab.api.dto.response.IsReviewedResponseDto;
import com.doova.ktab.api.dto.response.ReviewResponseDto;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.reviews.ReviewService;
import com.doova.ktab.utils.response.ResponseUtils;
import com.doova.ktab.utils.wrapper.ContentWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/reader", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Reader Book Review API", description = "Endpoints for posting and viewing book reviews.")
public class BookReviewController {

    private final ReviewService reviewService;

    // ============================================================================================
    // POST A REVIEW
    // ============================================================================================
    @Operation(summary = "Post a review for a book", description = "Allows a user to submit a rating and comment for a specific book.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Review submitted successfully")
    @PostMapping("/books/{bookId}/reviews")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<String>> postReview(@PathVariable Long bookId, @RequestBody ReviewRequestDto request, @CurrentUser User reader) {
        // NOTE: This operation typically requires authentication to identify the reviewer
        reviewService.createReview(bookId, request, reader);
        return ResponseUtils.response("Review submitted successfully.");
    }


    // ============================================================================================
    // GET ALL REVIEWS
    // ============================================================================================
    @Operation(summary = "Get all reviews for a book", description = "Returns all reviews posted for the specified book.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reviews retrieved successfully")
    @GetMapping("/books/{bookId}/reviews")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ContentWrapper<ReviewResponseDto>> viewReviews(@PathVariable Long bookId, @CurrentUser User reader) {
        List<ReviewResponseDto> reviews = reviewService.getReviewsByBookPrioritizeUser(bookId, reader.getId());
        return ResponseUtils.response(reviews);
    }

    @Operation(summary = "Check if user reviewed the book", description = "Returns whether the user has already reviewed the specified book.")
    @GetMapping("/books/{bookId}/isReviewed")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<IsReviewedResponseDto>> isReviewed(@PathVariable Long bookId, @CurrentUser User reader) {
        IsReviewedResponseDto result = reviewService.isReviewed(bookId, reader.getId());
        return ResponseUtils.response(result);
    }

    // ============================================================================================
    // UPDATE A REVIEW
    // ============================================================================================
    @Operation(summary = "Update an existing review", description = "Allows a user to update their rating or comment for a book.")
    @PatchMapping("/books/{bookId}/reviews/{reviewId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<String>> updateReview(@PathVariable Long bookId, @RequestBody ReviewRequestDto request, @PathVariable Long reviewId, @CurrentUser User reader) {
        reviewService.updateReview(bookId, request, reader, reviewId);
        return ResponseUtils.response("Review updated successfully.");
    }


    // ============================================================================================
    // DELETE A REVIEW
    // ============================================================================================
    @Operation(summary = "Delete a review", description = "Allows a user to delete their review for a book.")
    @DeleteMapping("/books/{bookId}/reviews/{reviewId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<String>> deleteReview(@PathVariable Long bookId, @PathVariable Long reviewId, @CurrentUser User reader) {
        reviewService.deleteReview(reviewId, bookId, reader.getId());
        return ResponseUtils.response("Review deleted successfully.");
    }
}