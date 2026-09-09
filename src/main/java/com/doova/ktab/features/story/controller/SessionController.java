package com.doova.ktab.features.story.controller;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.story.dto.ChoiceResponse;
import com.doova.ktab.features.story.dto.ChooseRequest;
import com.doova.ktab.features.story.dto.SessionResponse;
import com.doova.ktab.features.story.dto.TurnResponse;
import com.doova.ktab.features.story.model.ReadingSession;
import com.doova.ktab.features.story.model.Turn;
import com.doova.ktab.features.story.service.StorySessionService;
import com.doova.ktab.features.story.util.JsonUtil;
import com.doova.ktab.model.user.User;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import com.doova.ktab.utils.response.ResponseUtils;
import org.springframework.context.MessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApiVersion(1)
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
@Tag(name = "Interactive Story Sessions API", description = "Endpoints for starting and progressing interactive AI-driven story sessions.")
public class SessionController {

    private final StorySessionService storySessionService;
    private final FileStorageService fileStorageService;
    private final AttachmentService attachmentService;
    private final MessageSource messageSource;

    // ============================================================================================
    // START SESSION
    // ============================================================================================

    @Operation(summary = "Start a new interactive story session")
    @PostMapping("/start/{storyId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<SessionResponse>> startSession(@PathVariable Long storyId, @CurrentUser User reader) {
        List<Turn> turns = storySessionService.startSession(storyId, reader);
        return buildSessionResponse(turns, ApiMessageKey.SESSION_START_SUCCESS.getMessage(messageSource), HttpStatus.CREATED);
    }

    // ============================================================================================
    // MAKE A CHOICE
    // ============================================================================================

    @Operation(summary = "Choose an option and generate the next story turn")
    @PostMapping("/{sessionId}/choose")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<SessionResponse>> choose(@PathVariable Long sessionId, @RequestBody @Valid ChooseRequest request) {
        List<Turn> turns = storySessionService.chooseAndGenerateNext(sessionId, request.choiceId());
        return buildSessionResponse(turns, ApiMessageKey.SESSION_CHOOSE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    private ResponseEntity<ApiResponse<SessionResponse>> buildSessionResponse(List<Turn> turns, String message, HttpStatus status) {
        if (turns.isEmpty()) {
            throw new IllegalStateException("No turns found for session");
        }

        ReadingSession session = turns.getFirst().getSession();
        int storyScenes = session.getStory().getSceneCount();

        List<TurnResponse> turnResponses = turns.stream().map(this::mapToResponse).toList();

        SessionResponse response = new SessionResponse(session.getId(), storyScenes, turnResponses);
        return ResponseUtils.success(response, message, status);
    }

    // ============================================================================================
    // MAPPING
    // ============================================================================================

    @SuppressWarnings("unchecked")
    private TurnResponse mapToResponse(Turn turn) {
        Map<String, String> choices = JsonUtil.read(turn.getChoicesJson(), Map.class);
        boolean hasAnyChoice = choices != null && (choices.get("A") != null || choices.get("B") != null || choices.get("C") != null || choices.get("D") != null);

        // Generate signed URL from Attachment (using entityId/entityType pattern like Book)
        String imageUrl = null;
        Optional<Attachment> imageAttachment = attachmentService.getAttachment(turn.getId(), "Turn", "TURN_IMAGE");
        if (imageAttachment.isPresent()) {
            imageUrl = fileStorageService.getFileUrl(imageAttachment.get().getStoragePath(), UrlStrategy.SIGNED);
        }

        if (!hasAnyChoice) {
            // Final scene: no choices
            return new TurnResponse(turn.getTurnIndex(), turn.getSceneText(), imageUrl, turn.getChosenChoiceId(), null, null, null, null);
        }

        return new TurnResponse(
                turn.getTurnIndex(),
                turn.getSceneText(),
                imageUrl,
                turn.getChosenChoiceId(),
                new ChoiceResponse("A", choices.get("A")),
                new ChoiceResponse("B", choices.get("B")),
                new ChoiceResponse("C", choices.get("C")),
                new ChoiceResponse("D", choices.get("D"))
        );
    }
}
