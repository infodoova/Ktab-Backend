package com.doova.ktab.features.trailer.web;

import io.swagger.v3.oas.annotations.Operation;
import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/trailers", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN','AUTHOR','LIBRARIAN','ADMIN_LIBRARIAN')")
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
@Tag(name = "Book Trailer API", description = "Endpoints for creating, managing, and inspecting AI video trailers for books.")
public class TrailerController {

    private final TrailerService service;
    private final MessageSource messages;

    @Operation(summary = "Create a trailer for a book")
    @PostMapping("/books/{bookId}")
    public ResponseEntity<ApiResponse<TrailerView>> create(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(service.create(user, bookId), ApiMessageKey.TRAILER_CREATED.getMessage(messages), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "List a book's trailers, newest first")
    @GetMapping("/books/{bookId}")
    public ResponseEntity<ApiResponse<List<TrailerView>>> list(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(service.list(user, bookId), ApiMessageKey.TRAILER_FETCHED.getMessage(messages), HttpStatus.OK);
    }

    @Operation(summary = "Get one trailer and its status")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TrailerView>> get(@CurrentUser User user, @PathVariable Long id) {
        return ResponseUtils.success(service.get(user, id), ApiMessageKey.TRAILER_FETCHED.getMessage(messages), HttpStatus.OK);
    }

    @Operation(summary = "Get download links for a finished trailer")
    @GetMapping("/{id}/download")
    public ResponseEntity<ApiResponse<Map<String, String>>> download(@CurrentUser User user, @PathVariable Long id) {
        return ResponseUtils.success(service.downloadUrls(user, id), ApiMessageKey.TRAILER_FETCHED.getMessage(messages), HttpStatus.OK);
    }

    @Operation(summary = "Cancel a trailer that is still queued")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@CurrentUser User user, @PathVariable Long id) {
        service.cancel(user, id);
        return ResponseUtils.success(null, ApiMessageKey.TRAILER_CANCELLED.getMessage(messages), HttpStatus.OK);
    }

    @Operation(summary = "Approve or reject a trailer held for review (admin, the book's author or an admin librarian)")
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'AUTHOR', 'ADMIN_LIBRARIAN')")
    public ResponseEntity<ApiResponse<TrailerView>> review(
            @CurrentUser User reviewer,
            @PathVariable Long id,
            @RequestParam boolean approve) {
        return ResponseUtils.success(
                service.review(reviewer, id, approve),
                ApiMessageKey.TRAILER_REVIEWED.getMessage(messages),
                HttpStatus.OK);
    }

    @Operation(summary = "Queue a failed or held trailer again (admin)")
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<TrailerView>> retry(@CurrentUser User admin, @PathVariable Long id) {
        return ResponseUtils.success(
                service.retry(admin, id),
                ApiMessageKey.TRAILER_RETRY_QUEUED.getMessage(messages),
                HttpStatus.ACCEPTED);
    }
}
