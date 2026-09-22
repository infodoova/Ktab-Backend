package com.doova.ktab.controller.v1.admin;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.user.AssignPublisherRequest;
import com.doova.ktab.dto.user.UpdatePublisherRequest;
import com.doova.ktab.dto.user.UserResponseDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.service.publisher.PublisherAdminService;
import com.doova.ktab.utils.pagination.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/admin/publishers", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN')")
@Tag(name = "Admin Publisher Management API", description = "Endpoints for administrators to provision and manage publisher staff accounts.")
public class AdminUserRoleController {

    private final PublisherAdminService publisherAdminService;
    private final MessageSource messageSource;

    @Operation(summary = "Get all publisher accounts (paginated)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponseDto>>> getAllPublishers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search
    ) {
        PageResponse<UserResponseDto> result = publisherAdminService.getAllPublishers(page, size, search);
        return ResponseUtils.success(result, ApiMessageKey.PUBLISHER_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Assign or provision a publisher user account")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> assignPublisher(
            @Valid @RequestBody AssignPublisherRequest req
    ) {
        publisherAdminService.assignPublisher(req);
        return ResponseUtils.success(null, ApiMessageKey.PUBLISHER_ASSIGNED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Update a publisher account")
    @PutMapping(value = "/{userId}", consumes = "application/json")
    public ResponseEntity<ApiResponse<UserResponseDto>> updatePublisher(
            @PathVariable Long userId,
            @Valid @RequestBody UpdatePublisherRequest req
    ) {
        UserResponseDto updated = publisherAdminService.updatePublisher(userId, req);
        return ResponseUtils.success(updated, ApiMessageKey.PUBLISHER_ASSIGNED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Delete a publisher account")
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> removePublisher(
            @PathVariable Long userId
    ) {
        publisherAdminService.removePublisher(userId);
        return ResponseUtils.success(null, ApiMessageKey.PUBLISHER_REMOVED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
