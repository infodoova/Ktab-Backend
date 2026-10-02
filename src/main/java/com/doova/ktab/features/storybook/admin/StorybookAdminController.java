package com.doova.ktab.features.storybook.admin;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.utils.response.ResponseUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/admin/storybook", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN')")
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
@Tag(name = "Admin Storybook Management API", description = "Endpoints for administrators to review flagged storybook pages and manage storybook quotas.")
public class StorybookAdminController {

    private final StorybookAdminService admin;
    private final MessageSource messageSource;

    public record GrantRequest(@NotNull Long userId, @NotNull @Min(1) @Max(100) Integer units) {
    }

    @GetMapping("/flagged-pages")
    public ResponseEntity<ApiResponse<List<FlaggedPageView>>> flagged() {
        return ResponseUtils.success(admin.flaggedPages(), ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @PostMapping("/pages/{pageId}/accept")
    public ResponseEntity<ApiResponse<Void>> accept(@PathVariable Long pageId) {
        admin.accept(pageId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @PostMapping("/pages/{pageId}/regenerate")
    public ResponseEntity<ApiResponse<Void>> regenerate(@PathVariable Long pageId) {
        admin.regenerate(pageId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @PostMapping("/credits")
    public ResponseEntity<ApiResponse<Void>> grant(@Valid @RequestBody GrantRequest request) {
        admin.grantCredits(request.userId(), request.units());
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.OK);
    }
}
