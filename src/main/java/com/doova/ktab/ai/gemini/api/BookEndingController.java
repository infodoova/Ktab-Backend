package com.doova.ktab.ai.gemini.api;

import com.doova.ktab.ai.gemini.dto.request.GenerateEndingCommand;
import com.doova.ktab.ai.gemini.dto.response.GenerateEndingResponse;
import com.doova.ktab.ai.gemini.enums.TargetAudienceProfile;
import com.doova.ktab.ai.gemini.services.BookEndingService;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * HTTP layer: receives file + simple parameters and delegates to BookEndingService.
 */
@RestController
@RequestMapping("/api/v1/book-ending")
@RequiredArgsConstructor
@Tag(
        name = "Book Ending Generation API",
        description = "Endpoints for generating AI-powered alternate endings for uploaded book PDFs."
)
public class BookEndingController {

    private final BookEndingService bookEndingService;

    /**
     * Upload a book PDF and get a generated ending.
     * Multipart form-data:
     *  - file: the PDF
     *  - approxWordCountForEnding: e.g. 1500
     *  - audienceProfile: e.g. TEENS_13_16_DYSTOPIAN
     */
    @Operation(
            summary = "Generate an ending for a book PDF",
            description = """
                    Upload a book PDF and generate an alternate ending.
                    
                    Request (multipart/form-data):
                    - file: PDF of the book
                    - approxWordCountForEnding: approximate word count of the generated ending (e.g. 1500)
                    - audienceProfile: target audience key (e.g. TEENS_13_16_DYSTOPIAN)
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Ending generated successfully",
            content = @Content(schema = @Schema(implementation = GenerateEndingResponse.class))
    )
    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<GenerateEndingResponse>> generateEndingFromPdf(
            @RequestPart("file") MultipartFile file,
            @RequestParam("approxWordCountForEnding") int approxWordCountForEnding,
            @RequestParam("audienceProfile") String audienceProfileKey
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file must not be empty");
        }

        TargetAudienceProfile profile = TargetAudienceProfile.fromKey(audienceProfileKey);

        Resource pdfResource = new InputStreamResource(file.getInputStream());

        GenerateEndingCommand command = new GenerateEndingCommand(
                approxWordCountForEnding,
                profile,
                pdfResource
        );

        String ending = bookEndingService.generateEnding(command);
        GenerateEndingResponse responseBody = new GenerateEndingResponse(ending);

        // Same response pattern as AuthorBookController
        return ResponseUtils.response(responseBody, "Book ending generated successfully");
    }
}
