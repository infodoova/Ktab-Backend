package com.doova.ktab.controller.v1.reader;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.review.IsReviewedResponseDto;
import com.doova.ktab.dto.review.ReviewRequestDto;
import com.doova.ktab.dto.review.ReviewResponseDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.reviews.ReviewService;
import com.doova.ktab.utils.response.ResponseUtils;
import com.doova.ktab.utils.wrapper.ContentWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/reader", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Reader Book Review API", description = "Endpoints for posting and viewing book reviews.")
public class BookReviewController {

    private final ReviewService reviewService;
    private final MessageSource messageSource;

    // ============================================================================================
    // POST REVIEW
    // ============================================================================================
    @PostMapping("/books/{bookId}/reviews")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<String>> postReview(@PathVariable Long bookId, @Valid @RequestBody ReviewRequestDto request, @CurrentUser User reader) {
        reviewService.createReview(bookId, request, reader);

        return ResponseUtils.success(null, ApiMessageKey.REVIEW_CREATE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // GET REVIEWS
    // ============================================================================================
    @GetMapping("/books/{bookId}/reviews")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ContentWrapper<ReviewResponseDto>> viewReviews(@PathVariable Long bookId, @CurrentUser User reader, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseUtils.collection(reviewService.getReviewsByBookPrioritizeUser(bookId, reader.getId(), pageable));
    }


    // ============================================================================================
    // IS REVIEWED
    // ============================================================================================
    @GetMapping("/books/{bookId}/isReviewed")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<IsReviewedResponseDto>> isReviewed(@PathVariable Long bookId, @CurrentUser User reader) {
        IsReviewedResponseDto result = reviewService.isReviewed(bookId, reader.getId());

        return ResponseUtils.success(result, ApiMessageKey.REVIEW_IS_REVIEWED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // UPDATE REVIEW
    // ============================================================================================
    @PatchMapping("/books/{bookId}/reviews/{reviewId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<String>> updateReview(@PathVariable Long bookId, @PathVariable Long reviewId, @Valid @RequestBody ReviewRequestDto request, @CurrentUser User reader) {
        reviewService.updateReview(bookId, request, reader, reviewId);

        return ResponseUtils.success(null, ApiMessageKey.REVIEW_UPDATE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // DELETE REVIEW
    // ============================================================================================
    @DeleteMapping("/books/{bookId}/reviews/{reviewId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<String>> deleteReview(@PathVariable Long bookId, @PathVariable Long reviewId, @CurrentUser User reader) {
        reviewService.deleteReview(reviewId, bookId, reader.getId());

        return ResponseUtils.success(null, ApiMessageKey.REVIEW_DELETE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
