package com.doova.ktab.controller.v1.author;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.analytics.AuthorAnalyticsResponse;
import com.doova.ktab.dto.analytics.AuthorBookAnalyticsResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.author.AuthorAnalyticsService;
import com.doova.ktab.utils.pagination.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/authors", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('AUTHOR')")
@Tag(name = "Author Analytics API", description = "Analytics endpoints for authors, including performance metrics and per-book insights.")
public class AuthorAnalyticsController {

    private final AuthorAnalyticsService authorAnalyticsService;
    private final MessageSource messageSource;

    // ============================================================================================
    // AUTHOR OVERALL ANALYTICS
    // ============================================================================================

    @Operation(summary = "Get authenticated author's analytics")
    @GetMapping("/me/analytics")
    public ResponseEntity<ApiResponse<AuthorAnalyticsResponse>> getMyAnalytics(@CurrentUser User author) {
        AuthorAnalyticsResponse analytics = authorAnalyticsService.getAuthorAnalytics(author.getId());

        return ResponseUtils.success(analytics, ApiMessageKey.AUTHOR_ANALYTICS_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // AUTHOR BOOK ANALYTICS
    // ============================================================================================

    @Operation(summary = "Get authenticated author's book analytics")
    @GetMapping("/me/book-analytics")
    public ResponseEntity<ApiResponse<PageResponse<AuthorBookAnalyticsResponse>>> getMyBookAnalytics(
            @CurrentUser User author,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<AuthorBookAnalyticsResponse> analytics = authorAnalyticsService.getMyBooksWithAnalytics(
                author.getId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishDate"))
        );

        return ResponseUtils.success(PageResponse.fromPage(analytics), ApiMessageKey.AUTHOR_BOOK_ANALYTICS_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
