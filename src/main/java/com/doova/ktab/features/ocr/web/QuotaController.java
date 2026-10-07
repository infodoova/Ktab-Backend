package com.doova.ktab.features.ocr.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.ocr.quota.ConfigQuotaProvider;
import com.doova.ktab.features.ocr.quota.DynamicConcurrencyGate;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@ApiVersion(value = 1, keepLegacyPath = true)
@RequestMapping(path = "/ocr/quotas", produces = "application/json")
@PreAuthorize("hasAnyAuthority('ADMIN', 'ADMIN_LIBRARIAN')")
@Tag(name = "OCR Quota API", description = "Endpoints for viewing and updating OCR concurrency gates and quotas.")
public class QuotaController {

    private final ConfigQuotaProvider quota;
    private final DynamicConcurrencyGate gate;
    private final MessageSource messageSource;

    @Operation(summary = "Get the OCR concurrency quota and the gate's current limit")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> get() {
        return ResponseUtils.success(current(), ApiMessageKey.OCR_QUOTA_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Set the maximum number of parallel OCR requests")
    @PostMapping("/max-parallel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> set(@RequestParam int value) {
        quota.setMaxParallel(value);
        gate.refresh();
        return ResponseUtils.success(current(), ApiMessageKey.OCR_QUOTA_UPDATED.getMessage(messageSource), HttpStatus.OK);
    }

    private Map<String, Object> current() {
        return Map.of("maxParallelRequests", quota.currentMaxParallelRequests(), "gateCurrentMax", gate.currentMax());
    }
}
