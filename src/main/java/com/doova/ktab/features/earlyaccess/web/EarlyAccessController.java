package com.doova.ktab.features.earlyaccess.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.earlyaccess.service.EarlyAccessService;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessSignupRequest;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessSignupResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/public/early-access", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Early Access (public)", description = "Public signup for early access. Open without login.")
public class EarlyAccessController {

    private final EarlyAccessService service;
    private final MessageSource messageSource;

    @Operation(summary = "Ask for early access",
            description = "Registers the visitor's contact details. 409 when the email is already registered. Access itself is granted later by an admin.")
    @PostMapping
    public ResponseEntity<ApiResponse<EarlyAccessSignupResponse>> signUp(@Valid @RequestBody EarlyAccessSignupRequest request) {
        return ResponseUtils.success(service.signUp(request),
                ApiMessageKey.EARLY_ACCESS_REGISTERED.getMessage(messageSource), HttpStatus.CREATED);
    }
}
