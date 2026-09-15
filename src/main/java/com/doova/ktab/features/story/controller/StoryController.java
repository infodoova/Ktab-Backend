package com.doova.ktab.features.story.controller;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.story.dto.CreateStoryRequest;
import com.doova.ktab.features.story.dto.StoryResponse;
import com.doova.ktab.features.story.dto.UpdateStoryRequest;
import com.doova.ktab.features.story.service.StoryService;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.MessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping("/stories")
@RequiredArgsConstructor
@Tag(name = "Interactive Story Authoring API", description = "Endpoints for creating and managing AI-driven interactive story definitions.")
public class StoryController {

    private final StoryService storyService;
    private final MessageSource messageSource;

    // ============================================================================================
    // CREATE STORY
    // ============================================================================================

    @Operation(summary = "Create a new interactive story")
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoryResponse>> createStory(@RequestPart("story") @Valid CreateStoryRequest request, @RequestPart(value = "coverImage", required = false) MultipartFile coverImage, @CurrentUser User author) {
        StoryResponse response = storyService.createStory(request, coverImage, author);
        return ResponseUtils.success(response, ApiMessageKey.STORY_CREATE_SUCCESS.getMessage(messageSource), HttpStatus.CREATED);
    }

    @Operation(summary = "Get all interactive stories (paginated)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StoryResponse>>> getAllStoriesPaged(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<StoryResponse> response = storyService.getAllStoriesPaged(pageRequest);
        return ResponseUtils.success(response, ApiMessageKey.STORY_FETCH_ALL_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get a story by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoryResponse>> getStoryById(@PathVariable Long id) {
        StoryResponse response = storyService.getStoryById(id);
        return ResponseUtils.success(response, ApiMessageKey.STORY_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get all interactive stories by the current author (paginated)")
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<StoryResponse>>> getMyStories(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @CurrentUser User author) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<StoryResponse> response = storyService.getStoriesByAuthor(author.getId(), pageRequest);
        return ResponseUtils.success(response, ApiMessageKey.STORY_FETCH_MY_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Update a story")
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoryResponse>> updateStory(@PathVariable Long id, @RequestPart("story") @Valid UpdateStoryRequest request, @RequestPart(value = "coverImage", required = false) MultipartFile coverImage, @CurrentUser User author) {
        StoryResponse response = storyService.updateStory(id, request, coverImage, author);
        return ResponseUtils.success(response, ApiMessageKey.STORY_UPDATE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Delete a story")
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStory(@PathVariable Long id, @CurrentUser User author) {
        try {
            storyService.deleteStory(id, author);
            return ResponseUtils.success(null, ApiMessageKey.STORY_DELETE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
        } catch (IllegalStateException e) {
            // Check if the exception message matches our "has sessions" key
            if (ApiMessageKey.STORY_HAS_SESSIONS.getKey().equals(e.getMessage())) {
                return ResponseUtils.error(ApiMessageKey.STORY_HAS_SESSIONS.getMessage(messageSource), HttpStatus.CONFLICT);
            }
            throw e; // Rethrow other unexpected IllegalStateExceptions
        }
    }

}
