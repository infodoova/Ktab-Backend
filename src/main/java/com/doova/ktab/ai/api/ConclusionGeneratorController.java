package com.doova.ktab.ai.api;

import com.doova.ktab.ai.dto.request.ConclusionRequest;
import com.doova.ktab.ai.dto.response.ConclusionResponse;
import com.doova.ktab.ai.service.ConclusionGeneratorService;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequestMapping(path = "/api/v1/conclusion")
@RequiredArgsConstructor
@Validated
@Tag(name = "Conclusion Generator API",
        description = "Generate summaries and conclusions from uploaded PDF files using AI.")
public class ConclusionGeneratorController {

    private final ConclusionGeneratorService conclusionGeneratorService;

    // ============================================================================================
    // NON-STREAMING ENDPOINT
    // ============================================================================================
    @Operation(
            summary = "Generate a non-streamed AI conclusion",
            description = "Uploads a PDF and generates a full summary/conclusion in one response."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Conclusion generated",
            content = @Content(schema = @Schema(implementation = ConclusionResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request or file error"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Server error"
    )
    @PostMapping(
            value = "/generate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<ConclusionResponse>> generateConclusion(
            @ModelAttribute @Validated ConclusionRequest request
    ) {
        try {

            String result = conclusionGeneratorService.fetchConclusion(request);

            return ResponseUtils.response(
                    new ConclusionResponse(result),
                    "Conclusion generated successfully"
            );

        } catch (IllegalArgumentException | IOException ex) {
            return ResponseUtils.badRequest("Invalid file: " + ex.getMessage());
        } catch (Exception ex) {
            // 4. Catches all other unexpected errors
            throw  ResponseUtils.errorResponse(
                    "Unexpected error during conclusion generation.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }


    // ============================================================================================
    // STREAMING (SSE) ENDPOINT
    // ============================================================================================
    @Operation(
            summary = "Stream a conclusion (Server-Sent Events)",
            description = "Uploads a PDF and streams the generated AI text token-by-token."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Streaming response",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request or file error"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Server error"
    )
    @PostMapping(
            value = "/stream",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<String> streamConclusion(
            @ModelAttribute @Validated ConclusionRequest request
    ) throws Exception {

        return conclusionGeneratorService.streamConclusion(request)

                // Combine small GPT-4o deltas smoothly
                .windowTimeout(20, Duration.ofMillis(150))
                .flatMap(window ->
                        window.reduce(new StringBuilder(), StringBuilder::append)
                                .map(StringBuilder::toString)
                )

                // Clean final chunks
                .filter(chunk -> chunk != null && !chunk.isBlank())

                // Final output wrapper for SSE
                .map(chunk -> chunk + "\n\n")

                .onErrorResume(e ->
                        Flux.just("data:STREAM_ERROR " + e.getMessage() + "\n\n")
                );
    }

}
