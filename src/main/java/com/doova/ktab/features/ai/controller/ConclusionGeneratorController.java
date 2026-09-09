package com.doova.ktab.features.ai.controller;

import com.doova.ktab.features.ai.dto.request.ConclusionRequest;
import com.doova.ktab.features.ai.dto.response.ConclusionResponse;
import com.doova.ktab.features.ai.service.ConclusionGeneratorService;
import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/conclusion")
@RequiredArgsConstructor
@Validated
@Tag(name = "Conclusion Generator API", description = "Generate summaries and conclusions from uploaded PDF files using AI.")
public class ConclusionGeneratorController {

    private final ConclusionGeneratorService conclusionGeneratorService;
    private final MessageSource messageSource;

    // ============================================================================================
    // NON-STREAMING ENDPOINT
    // ============================================================================================
    @Operation(summary = "Generate a non-streamed AI conclusion", description = "Uploads a PDF and generates a full summary/conclusion in one response.")
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ConclusionResponse>> generateConclusion(@ModelAttribute @Validated ConclusionRequest request) {

        try {
            String result = conclusionGeneratorService.fetchConclusion(request);

            return ResponseUtils.success(new ConclusionResponse(result), ApiMessageKey.AI_CONCLUSION_GENERATE_SUCCESS.getMessage(messageSource), HttpStatus.OK);

        } catch (IllegalArgumentException | IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ApiMessageKey.AI_CONCLUSION_INVALID_FILE.getMessage(messageSource), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ApiMessageKey.AI_CONCLUSION_UNEXPECTED_ERROR.getMessage(messageSource), ex);
        }
    }

    // ============================================================================================
    // STREAMING (SSE) ENDPOINT
    // ============================================================================================
    @Operation(summary = "Stream a conclusion (Server-Sent Events)", description = "Uploads a PDF and streams the generated AI text token-by-token.")
    @PostMapping(value = "/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamConclusion(@ModelAttribute @Validated ConclusionRequest request) {

        return conclusionGeneratorService.streamConclusion(request)

                // Smooth token batching
                .windowTimeout(20, Duration.ofMillis(150)).flatMap(window -> window.reduce(new StringBuilder(), StringBuilder::append).map(StringBuilder::toString))

                // Clean output
                .filter(chunk -> chunk != null && !chunk.isBlank()).map(chunk -> chunk + "\n\n")

                // SSE-safe error handling
                .onErrorResume(ex -> Flux.just("data:" + ApiMessageKey.AI_CONCLUSION_STREAM_ERROR.getMessage(messageSource) + "\n\n"));
    }
}
