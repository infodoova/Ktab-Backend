package com.doova.ktab.controller.v1.internal;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.features.ocr.dto.OcrCallbackResult;
import com.doova.ktab.features.ocr.service.OcrCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller endpoint for receiving asynchronous webhook callbacks from Upstash QStash.
 * Adheres strictly to Separation of Concerns: delegates all verification, parsing, metrics,
 * and processing logic to {@link OcrCallbackService}.
 */
@ApiVersion(1)
@RestController
@RequestMapping(path = "/internal/ocr", produces = "application/json")
@RequiredArgsConstructor
@Slf4j
public class OcrCallbackController {

    private final OcrCallbackService ocrCallbackService;

    @PostMapping(value = "/process", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> processOcrPage(
            @RequestHeader(value = "Upstash-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        OcrCallbackResult result = ocrCallbackService.processCallback(signature, rawBody);

        return switch (result) {
            case SUCCESS, SKIPPED -> ResponseEntity.ok().build();
            case INVALID_SIGNATURE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case INVALID_PAYLOAD -> ResponseEntity.badRequest().build();
            case PROCESSING_FAILED -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        };
    }
}
