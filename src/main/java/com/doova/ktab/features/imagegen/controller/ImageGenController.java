package com.doova.ktab.features.imagegen.controller;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.imagegen.dto.request.GenerateImageRequest;
import com.doova.ktab.features.imagegen.dto.response.GenerateImageResponse;
import com.doova.ktab.features.imagegen.dto.response.ImageStatusResponse;
import com.doova.ktab.features.imagegen.service.ImageGenService;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/books/{bookId}/images")
@RequiredArgsConstructor
@Validated
@Tag(name = "Image Content Generator", description = "AI-powered creative book scene illustration generator backed by Cloudflare R2")
public class ImageGenController {

    private final ImageGenService imageGenService;
    private final MessageSource messageSource;

    @Operation(
            summary = "Request book illustration generation",
            description = "Submits a user scene context, artistic theme, and aspect ratio for asynchronous AI image generation. Returns 202 Accepted."
    )
    @PostMapping("/generate")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<ImageStatusResponse>> generateImage(
            @PathVariable @NotNull @Positive Long bookId,
            @RequestBody @Valid GenerateImageRequest request,
            @CurrentUser User user
    ) {
        ImageStatusResponse response = imageGenService.submitGeneration(bookId, request, user);
        return ResponseUtils.success(
                response,
                ApiMessageKey.IMAGE_GEN_SUBMIT_SUCCESS.getMessage(messageSource),
                HttpStatus.ACCEPTED
        );
    }

    @Operation(
            summary = "Check image generation status",
            description = "Polls the status of an image generation job. Once completed, includes the resolved Cloudflare image URL."
    )
    @GetMapping("/{imageId}/status")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<ImageStatusResponse>> getImageStatus(
            @PathVariable @NotNull @Positive Long bookId,
            @PathVariable @NotNull UUID imageId,
            @CurrentUser User user
    ) {
        ImageStatusResponse response = imageGenService.getStatus(bookId, imageId, user);
        return ResponseUtils.success(
                response,
                ApiMessageKey.IMAGE_GEN_STATUS_FETCH_SUCCESS.getMessage(messageSource),
                HttpStatus.OK
        );
    }

    @Operation(
            summary = "Get single generated image details",
            description = "Retrieves full creative context, aesthetic parameters, and resolved Cloudflare image URL for a single generated image."
    )
    @GetMapping("/{imageId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<GenerateImageResponse>> getImage(
            @PathVariable @NotNull @Positive Long bookId,
            @PathVariable @NotNull UUID imageId,
            @CurrentUser User user
    ) {
        GenerateImageResponse response = imageGenService.getImage(bookId, imageId, user);
        return ResponseUtils.success(
                response,
                ApiMessageKey.IMAGE_GEN_STATUS_FETCH_SUCCESS.getMessage(messageSource),
                HttpStatus.OK
        );
    }

    @Operation(
            summary = "List generated images for book",
            description = "Retrieves a paginated list of generated illustrations for the specified book belonging to the current user."
    )
    @GetMapping
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<Page<GenerateImageResponse>>> listImages(
            @PathVariable @NotNull @Positive Long bookId,
            @CurrentUser User user,
            @PageableDefault(size = 12) Pageable pageable
    ) {
        Page<GenerateImageResponse> page = imageGenService.listImages(bookId, user, pageable);
        return ResponseUtils.success(
                page,
                ApiMessageKey.IMAGE_GEN_LIST_FETCH_SUCCESS.getMessage(messageSource),
                HttpStatus.OK
        );
    }

    @Operation(
            summary = "Delete generated image",
            description = "Deletes a generated illustration record and removes its artifact from Cloudflare R2 storage."
    )
    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<Void> deleteImage(
            @PathVariable @NotNull @Positive Long bookId,
            @PathVariable @NotNull UUID imageId,
            @CurrentUser User user
    ) {
        imageGenService.deleteImage(bookId, imageId, user);
        return ResponseUtils.noContent();
    }

    @Operation(
            summary = "Get available image generator filters",
            description = "Returns supported themes and aspect ratios with display labels and style descriptions for UI rendering."
    )
    @GetMapping("/filters")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<com.doova.ktab.features.imagegen.dto.response.ImageGenFiltersResponse>> getFilters(
            @PathVariable @NotNull @Positive Long bookId
    ) {
        var filters = imageGenService.getAvailableFilters();
        return ResponseUtils.success(
                filters,
                ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource),
                HttpStatus.OK
        );
    }
}
