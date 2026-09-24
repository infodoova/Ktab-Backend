package com.doova.ktab.features.storybook.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.web.dto.ChildProfileResponse;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/storybook/children", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
public class ChildProfileController {

    private final ChildProfileService service;
    private final MessageSource messageSource;

    @PostMapping
    public ResponseEntity<ApiResponse<ChildProfileResponse>> create(@CurrentUser User user,
                                                                    @Valid @RequestBody CreateChildProfileRequest request) {
        return ResponseUtils.success(service.create(user, request),
                ApiMessageKey.STORYBOOK_CHILD_SAVED.getMessage(messageSource), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChildProfileResponse>>> list(@CurrentUser User user) {
        return ResponseUtils.success(service.list(user),
                ApiMessageKey.STORYBOOK_CHILD_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @PutMapping("/{childId}")
    public ResponseEntity<ApiResponse<ChildProfileResponse>> update(@CurrentUser User user, @PathVariable Long childId,
                                                                    @Valid @RequestBody CreateChildProfileRequest request) {
        return ResponseUtils.success(service.update(user, childId, request),
                ApiMessageKey.STORYBOOK_CHILD_SAVED.getMessage(messageSource), HttpStatus.OK);
    }

    @DeleteMapping("/{childId}")
    public ResponseEntity<ApiResponse<Void>> delete(@CurrentUser User user, @PathVariable Long childId) {
        service.delete(user, childId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_CHILD_DELETED.getMessage(messageSource), HttpStatus.OK);
    }
}
