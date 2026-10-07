package com.doova.ktab.features.storybook.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.features.storybook.web.dto.StorybookSummary;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/storybook", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
@Tag(name = "Storybook API", description = "Endpoints for creating, managing, and generating AI-powered personalized children storybooks.")
public class StorybookController {

    private final StorybookService service;
    private final MessageSource messageSource;
    private final com.doova.ktab.features.storybook.orchestrator.StorybookResumeService resumeService;
    private final com.doova.ktab.features.storybook.story.pipeline.StoryApprovalService storyApprovalService;
    private final com.doova.ktab.features.storybook.character.PhotoIntakeService photoIntakeService;
    private final com.doova.ktab.features.storybook.illustration.LookService lookService;
    private final com.doova.ktab.features.storybook.illustration.PageRegenerationService pageRegenerationService;
    private final com.doova.ktab.features.storybook.render.ReaderService readerService;
    private final CancelService cancelService;

    @Operation(summary = "Regenerate one page illustration of a finished book")
    @PostMapping("/books/{bookId}/pages/{pageIndex}/regenerate")
    public ResponseEntity<ApiResponse<Void>> regeneratePage(@CurrentUser User user, @PathVariable Long bookId,
                                                            @PathVariable int pageIndex) {
        pageRegenerationService.regenerate(user, bookId, pageIndex);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_PAGE_REGENERATING.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "Regenerate the character sheet (look) of a book")
    @PostMapping("/books/{bookId}/character/regenerate")
    public ResponseEntity<ApiResponse<Void>> regenerateLook(@CurrentUser User user, @PathVariable Long bookId) {
        lookService.regenerate(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_LOOK_REGENERATING.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "Approve the character sheet and start illustrating the pages")
    @PostMapping("/books/{bookId}/character/approve")
    public ResponseEntity<ApiResponse<Void>> approveLook(@CurrentUser User user, @PathVariable Long bookId) {
        lookService.approve(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_LOOK_APPROVED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "Create a new storybook via JSON", description = "Creates a storybook draft. Supports embedded base64 child and character photos in the payload.")
    @PostMapping(path = "/books", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StorybookDetail>> create(@CurrentUser User user,
                                                               @Valid @RequestBody CreateStorybookRequest request) {
        return ResponseUtils.success(service.create(user, request),
                ApiMessageKey.STORYBOOK_CREATED.getMessage(messageSource), HttpStatus.CREATED);
    }

    @Operation(summary = "Create a new storybook with photo uploads", description = "Creates a storybook draft and uploads child, companion, and character photos in a single multipart request.")
    @PostMapping(path = "/books", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StorybookDetail>> createMultipart(
            @CurrentUser User user,
            @Valid @RequestPart("request") CreateStorybookRequest request,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "childPhoto", required = false) MultipartFile childPhoto,
            @RequestPart(value = "companionPhoto", required = false) MultipartFile companionPhoto,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
            @RequestPart(value = "characterPhotos", required = false) List<MultipartFile> characterPhotos,
            @RequestParam(value = "consent", required = false) Boolean consent) {
        MultipartFile effectiveChild = (childPhoto != null && !childPhoto.isEmpty()) ? childPhoto : photo;
        List<MultipartFile> effectiveOthers = (characterPhotos != null && !characterPhotos.isEmpty()) ? characterPhotos : photos;
        return ResponseUtils.success(service.create(user, request, effectiveChild, companionPhoto, effectiveOthers, consent),
                ApiMessageKey.STORYBOOK_CREATED.getMessage(messageSource), HttpStatus.CREATED);
    }

    @Operation(summary = "Upload the child's photo for a storybook")
    @PostMapping(path = "/books/{bookId}/photo", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Void>> uploadPhoto(@CurrentUser User user, @PathVariable Long bookId,
                                                         @RequestParam("photo") org.springframework.web.multipart.MultipartFile photo,
                                                         @RequestParam("consent") boolean consent) {
        photoIntakeService.upload(user, bookId, photo, consent);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_PHOTO_UPLOADED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "Resume a failed storybook from where it stopped")
    @PostMapping("/books/{bookId}/resume")
    public ResponseEntity<ApiResponse<Void>> resume(@CurrentUser User user, @PathVariable Long bookId) {
        resumeService.resume(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_RESUMED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "Edit story text and title before Gate 1 approval", description = "Updates the storybook title and/or page story texts and visual scenes while the book is in STORY_READY status before Gate 1 approval.")
    @PutMapping("/books/{bookId}/story")
    public ResponseEntity<ApiResponse<StorybookDetail>> editStory(
            @CurrentUser User user,
            @PathVariable Long bookId,
            @Valid @RequestBody com.doova.ktab.features.storybook.web.dto.EditStoryRequest request) {
        return ResponseUtils.success(service.editStory(user, bookId, request),
                ApiMessageKey.STORYBOOK_STORY_UPDATED.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Approve the story text and start the character sheet")
    @PostMapping("/books/{bookId}/story/approve")
    public ResponseEntity<ApiResponse<Void>> approveStory(@CurrentUser User user, @PathVariable Long bookId) {
        storyApprovalService.approveStory(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_STORY_APPROVED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "Cancel a storybook")
    @PostMapping("/books/{bookId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@CurrentUser User user, @PathVariable Long bookId) {
        cancelService.cancel(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_CANCELLED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "List the caller's storybooks")
    @GetMapping("/books")
    public ResponseEntity<ApiResponse<List<StorybookSummary>>> list(@CurrentUser User user) {
        return ResponseUtils.success(service.list(user), ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get one storybook with its pages and status")
    @GetMapping("/books/{bookId}")
    public ResponseEntity<ApiResponse<StorybookDetail>> get(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(service.detail(user, bookId), ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get the reader manifest of a finished storybook")
    @GetMapping("/books/{bookId}/reader")
    public ResponseEntity<ApiResponse<com.doova.ktab.features.storybook.render.ReaderManifest>> reader(
            @CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(readerService.manifest(user, bookId),
                ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get a download link for a finished storybook's PDF")
    @GetMapping("/books/{bookId}/download")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> download(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(java.util.Map.of("url", readerService.downloadUrl(user, bookId)),
                ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }
}
