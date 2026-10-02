package com.doova.ktab.features.talktobook.controller;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.talktobook.dto.request.TalkToBookRequest;
import com.doova.ktab.features.talktobook.dto.response.TalkToBookResponse;
import com.doova.ktab.features.talktobook.service.TalkToBookService;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/books/{bookId}/talk")
@RequiredArgsConstructor
@Validated
@Tag(name = "Talk to Book API", description = "Endpoints for interactive conversational AI agent strictly grounded in book content.")
public class TalkToBookController {

    private final TalkToBookService talkToBookService;
    private final MessageSource messageSource;

    @Operation(
            summary = "Ask a question about a book",
            description = "Submits a question to the Talk-to-Book AI assistant. Returns an answer strictly grounded in the book content or cached results."
    )
    @PostMapping
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<TalkToBookResponse>> askQuestion(
            @PathVariable @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long bookId,
            @RequestBody @Valid TalkToBookRequest request,
            @CurrentUser User user) {

        TalkToBookResponse response = talkToBookService.askQuestion(bookId, request, user != null ? user.getId() : null);

        return ResponseUtils.success(
                response,
                ApiMessageKey.TALK_TO_BOOK_ANSWER_SUCCESS.getMessage(messageSource),
                HttpStatus.OK
        );
    }
}
