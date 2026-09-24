package com.doova.ktab.features.storybook.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.web.dto.BlueprintSummary;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.features.storybook.web.dto.StorybookSummary;
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
@RequestMapping(path = "/storybook", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
public class StorybookController {

    private final StorybookService service;
    private final MessageSource messageSource;
    private final com.doova.ktab.features.storybook.orchestrator.StorybookResumeService resumeService;
    private final com.doova.ktab.features.storybook.story.pipeline.StoryApprovalService storyApprovalService;
    private final com.doova.ktab.features.storybook.character.PhotoIntakeService photoIntakeService;
    private final com.doova.ktab.features.storybook.illustration.LookService lookService;
    private final com.doova.ktab.features.storybook.illustration.PageRegenerationService pageRegenerationService;

    @PostMapping("/books/{bookId}/pages/{pageIndex}/regenerate")
    public ResponseEntity<ApiResponse<Void>> regeneratePage(@CurrentUser User user, @PathVariable Long bookId,
                                                            @PathVariable int pageIndex) {
        pageRegenerationService.regenerate(user, bookId, pageIndex);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @PostMapping("/books/{bookId}/character/regenerate")
    public ResponseEntity<ApiResponse<Void>> regenerateLook(@CurrentUser User user, @PathVariable Long bookId) {
        lookService.regenerate(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @PostMapping("/books/{bookId}/character/approve")
    public ResponseEntity<ApiResponse<Void>> approveLook(@CurrentUser User user, @PathVariable Long bookId) {
        lookService.approve(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @PostMapping("/books")
    public ResponseEntity<ApiResponse<StorybookDetail>> create(@CurrentUser User user,
                                                               @Valid @RequestBody CreateStorybookRequest request) {
        return ResponseUtils.success(service.create(user, request),
                ApiMessageKey.STORYBOOK_CREATED.getMessage(messageSource), HttpStatus.CREATED);
    }

    @PostMapping(path = "/books/{bookId}/photo", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Void>> uploadPhoto(@CurrentUser User user, @PathVariable Long bookId,
                                                         @RequestParam("photo") org.springframework.web.multipart.MultipartFile photo,
                                                         @RequestParam("consent") boolean consent) {
        photoIntakeService.upload(user, bookId, photo, consent);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @PostMapping("/books/{bookId}/resume")
    public ResponseEntity<ApiResponse<Void>> resume(@CurrentUser User user, @PathVariable Long bookId) {
        resumeService.resume(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @PostMapping("/books/{bookId}/story/approve")
    public ResponseEntity<ApiResponse<Void>> approveStory(@CurrentUser User user, @PathVariable Long bookId) {
        storyApprovalService.approveStory(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @GetMapping("/books")
    public ResponseEntity<ApiResponse<List<StorybookSummary>>> list(@CurrentUser User user) {
        return ResponseUtils.success(service.list(user), ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @GetMapping("/books/{bookId}")
    public ResponseEntity<ApiResponse<StorybookDetail>> get(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(service.detail(user, bookId), ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @GetMapping("/blueprints")
    public ResponseEntity<ApiResponse<List<BlueprintSummary>>> blueprints(@RequestParam AgeBand ageBand) {
        return ResponseUtils.success(service.blueprints(ageBand),
                ApiMessageKey.STORYBOOK_BLUEPRINTS_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }
}
