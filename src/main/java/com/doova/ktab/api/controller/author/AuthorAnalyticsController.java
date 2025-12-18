package com.doova.ktab.api.controller.author;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.api.dto.response.AuthorAnalyticsResponse;
import com.doova.ktab.api.dto.response.AuthorBookAnalyticsResponse;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.author.AuthorAnalyticsService;
import com.doova.ktab.utils.response.ResponseUtils;
import com.doova.ktab.utils.wrapper.ContentWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping("/authors")
@RequiredArgsConstructor
@Tag(
        name = "Author Analytics API",
        description = "Analytics endpoints for authors, including performance metrics and per-book insights."
)
public class AuthorAnalyticsController {

    private final AuthorAnalyticsService authorAnalyticsService;

    // ============================================================================================
    // AUTHOR OVERALL ANALYTICS
    // ============================================================================================
    @Operation(
            summary = "Get authenticated author's analytics",
            description = "Returns overall analytics for the logged-in author including total books, reviews, ratings, and growth vs last month."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Author analytics retrieved successfully",
            content = @Content(schema = @Schema(implementation = AuthorAnalyticsResponse.class))
    )
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    @GetMapping("/me/analytics")
    public ResponseEntity<ApiResponse<AuthorAnalyticsResponse>> getMyAnalytics(
            @CurrentUser User author
    ) {
        AuthorAnalyticsResponse analytics =
                authorAnalyticsService.getAuthorAnalytics(author.getId());

        return ResponseUtils.response(analytics);
    }

    // ============================================================================================
    // AUTHOR BOOK ANALYTICS
    // ============================================================================================
    @Operation(
            summary = "Get authenticated author's book analytics",
            description = "Returns analytics per book including reviews count, average rating, and publish date (if published)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Author book analytics retrieved successfully",
            content = @Content(schema = @Schema(implementation = AuthorBookAnalyticsResponse.class))
    )
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    @GetMapping("/me/book-analytics")
    public ResponseEntity<Page<AuthorBookAnalyticsResponse>> getMyBookAnalytics(
            @CurrentUser User author,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<AuthorBookAnalyticsResponse> analytics =
                authorAnalyticsService.getMyBooksWithAnalytics(
                        author.getId(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishDate"))
                );

        return ResponseUtils.response(analytics);
    }
}
