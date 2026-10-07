package com.doova.ktab.features.trailer.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/books", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('READER', 'ADMIN', 'LIBRARIAN', 'ADMIN_LIBRARIAN', 'AUTHOR')")
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
@Tag(name = "Book Trailer (reader)", description = "The finished trailer of a published book, for readers.")
public class ReaderTrailerController {

    private final ReaderTrailerService service;
    private final MessageSource messages;

    @Operation(summary = "Latest trailer of a published book",
            description = "Short-lived links to the newest READY trailer. 404 when the book is not published or has no finished trailer.")
    @GetMapping("/{bookId}/trailer")
    public ResponseEntity<ApiResponse<ReaderTrailerView>> latest(@PathVariable Long bookId) {
        return ResponseUtils.success(service.latest(bookId), ApiMessageKey.TRAILER_FETCHED.getMessage(messages), HttpStatus.OK);
    }
}
