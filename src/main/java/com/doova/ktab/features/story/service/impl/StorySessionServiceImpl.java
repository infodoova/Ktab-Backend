package com.doova.ktab.features.story.service.impl;

import com.doova.ktab.features.story.dto.GeneratedTurn;
import com.doova.ktab.features.story.dto.MemorySummary;
import com.doova.ktab.features.story.enums.ChoiceId;
import com.doova.ktab.features.story.enums.SessionStatus;
import com.doova.ktab.features.story.image.*;
import com.doova.ktab.features.story.model.*;
import com.doova.ktab.features.story.repository.*;
import com.doova.ktab.features.story.scd.SceneCanonicalDescription;
import com.doova.ktab.features.story.scd.ScdPromptFactory;
import com.doova.ktab.features.story.service.ChoiceEffectResolver;
import com.doova.ktab.features.story.service.ContextBuilder;
import com.doova.ktab.features.story.service.PromptFactory;

import com.doova.ktab.features.story.service.SessionStateFactory;
import com.doova.ktab.features.story.service.SpringAiStoryClient;
import com.doova.ktab.features.story.service.StorySessionService;
import com.doova.ktab.features.story.util.JsonUtil;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorySessionServiceImpl implements StorySessionService {

    private final StoryRepository storyRepository;
    private final SessionRepository sessionRepo;
    private final SpringAiStoryClient ai;
    private final PromptFactory prompts;
    private final ContextBuilder contextBuilder;
    private final SessionStateFactory sessionStateFactory;
    private final VertexImageClient imageClient;
    private final FileStorageService fileStorageService;
    private final AttachmentService attachmentService;
    private final TurnRepository turnRepository;


    @Value("${app.story.maxSceneWords:220}")
    private int maxSceneWords;
    @Value("${app.story.keepLastRawTurns:2}")
    private int keepLastRawTurns;
    @Value("${app.story.summarizeEveryTurns:4}")
    private int summarizeEveryTurns;
    @Value("${app.story.maxScenesDefault:12}")
    private int maxScenesDefault;

    @Override
    @Transactional
    public List<Turn> startSession(Long storyId, User reader) {
        try {
            var existingSession = sessionRepo.findByStoryIdAndReaderId(storyId, reader.getId());

            if (existingSession.isPresent()) {
                ReadingSession session = existingSession.get();
                return session.getTurns().stream().sorted(Comparator.comparingInt(Turn::getTurnIndex).reversed()).toList();
            }

            Story story = storyRepository.findById(storyId).orElseThrow(() -> new IllegalArgumentException("Story not found"));
            int maxScenes = effectiveMaxScenes(story);

            SessionState initialState = sessionStateFactory.initialStateFor(story);

            ReadingSession session = new ReadingSession(story, reader, initialState);
            session.setStory(story);

            String sys = prompts.storytellerSystem(story, maxSceneWords);
            String user = contextBuilder.buildTurnUserPrompt(session.getRollingSummary(), List.of(), session.getState(), "", 1, maxScenes);

            GeneratedTurn gen = ai.generateTurn(sys, user);
            validateGeneratedTurn(gen, 1 >= maxScenes);

            String choicesJson = (gen.choices() == null) ? "{}" : JsonUtil.write(gen.choices());
            Turn t1 = new Turn(session, 1, gen.sceneText(), choicesJson);
            session.addTurn(t1);
            if (1 >= maxScenes) {
                session.setStatus(SessionStatus.COMPLETED);
            }
            sessionRepo.saveAndFlush(session); // Save and flush to ensure turn has ID

            try {
                generateAndAttachImage(t1, story);
            } catch (Exception e) {
                // If image generation fails, remove the turn and rethrow
                session.removeTurn(t1);
                sessionRepo.save(session);
                throw e;
            }

            return List.of(t1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public List<Turn> chooseAndGenerateNext(Long sessionId, String choiceRaw) {
        try {
            ChoiceId choice = ChoiceId.from(choiceRaw);

            ReadingSession session = sessionRepo.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("Session not found"));
            if (session.getStatus() != SessionStatus.ACTIVE) {
                throw new IllegalStateException("Session is not active");
            }

            Story story = session.getStory();
            int maxScenes = effectiveMaxScenes(story);

            Turn current = session.getTurns().stream().max(Comparator.comparingInt(Turn::getTurnIndex)).orElseThrow(() -> new IllegalStateException("No turns for session"));
            if (current.getTurnIndex() >= maxScenes) {
                session.setStatus(SessionStatus.COMPLETED);
                throw new IllegalStateException("Reached max scenes for this story");
            }
            Map<?, ?> choices = JsonUtil.read(current.getChoicesJson(), Map.class);

            if (!choices.containsKey(choice.name())) {
                throw new IllegalArgumentException("Choice not available");
            }
            current.setChosenChoiceId(choice.name());

            SessionState nextState = ChoiceEffectResolver.apply(session.getState(), choice);

            session.setState(nextState);

            List<Turn> lastRaw = session.getTurns().stream().sorted(Comparator.comparingInt(Turn::getTurnIndex)).filter(t -> !t.isSummarized()).toList();

            if (lastRaw.size() > keepLastRawTurns) {
                lastRaw = lastRaw.subList(lastRaw.size() - keepLastRawTurns, lastRaw.size());
            }

            String sys = prompts.storytellerSystem(story, maxSceneWords);
            int nextTurnIndex = current.getTurnIndex() + 1;
            if (nextTurnIndex > maxScenes) {
                session.setStatus(SessionStatus.COMPLETED);
                throw new IllegalStateException("Reached max scenes for this story");
            }
            String user = contextBuilder.buildTurnUserPrompt(session.getRollingSummary(), lastRaw, session.getState(), choice.name(), nextTurnIndex, maxScenes);

            GeneratedTurn gen = ai.generateTurn(sys, user);
            validateGeneratedTurn(gen, nextTurnIndex >= maxScenes);

            String nextChoicesJson = (gen.choices() == null) ? "{}" : JsonUtil.write(gen.choices());
            Turn nextTurn = new Turn(session, current.getTurnIndex() + 1, gen.sceneText(), nextChoicesJson);
            session.addTurn(nextTurn);
            nextTurn.setSession(session);
            if (nextTurn.getTurnIndex() >= maxScenes) {
                session.setStatus(SessionStatus.COMPLETED);
            }
            nextTurn = turnRepository.saveAndFlush(nextTurn); // Save and flush to ensure turn has ID

            // Generate image (best effort)
            try {
                generateAndAttachImage(nextTurn, story);
            } catch (Exception e) {
                // If image generation fails, remove the turn and rethrow
                session.removeTurn(nextTurn);
                sessionRepo.save(session);
                throw e;
            }

            maybeSummarize(session, story);

            sessionRepo.save(session);

            return session.getTurns().stream().sorted(Comparator.comparingInt(Turn::getTurnIndex).reversed()).toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int effectiveMaxScenes(Story story) {
        int configured = Math.max(1, maxScenesDefault);
        int perStory = story.getSceneCount();
        // Backward compatible: older stories may have default 1, but we still want a usable session length.
        int effective = perStory >= 2 ? perStory : configured;
        // Safety clamp against accidental huge values
        return Math.min(effective, 200);
    }

    private void maybeSummarize(ReadingSession session, Story story) {
        int lastSumm = session.getLastSummarizedTurnIndex();
        int end = lastSumm + summarizeEveryTurns;
        List<Turn> allTurns = session.getTurns().stream().sorted(Comparator.comparingInt(Turn::getTurnIndex)).toList();

        if (allTurns.isEmpty()) {
            return;
        }

        int maxTurnIndex = allTurns.getLast().getTurnIndex();
        if (maxTurnIndex < end) {
            return;
        }

        List<Turn> batch = allTurns.stream().filter(t -> t.getTurnIndex() > lastSumm && t.getTurnIndex() <= end && !t.isSummarized()).toList();

        if (batch.size() < summarizeEveryTurns) {
            return;
        }

        String sys = prompts.summarizerSystem(story);
        String user = contextBuilder.buildSummaryUserPrompt(session.getRollingSummary(), session.getState(), batch);

        MemorySummary summary = ai.summarize(sys, user);
        session.setRollingSummary(JsonUtil.write(summary));
        session.setLastSummarizedTurnIndex(end);

        batch.forEach(t -> t.setSummarized(true));
    }

    private void validateGeneratedTurn(GeneratedTurn gen, boolean isFinalTurn) {
        if (gen == null || gen.sceneText() == null || gen.sceneText().isBlank())
            throw new IllegalStateException("AI returned empty sceneText");

        if (isFinalTurn) {
            // Final turn: no choices expected.
            if (gen.choices() != null && !gen.choices().isEmpty()) {
                throw new IllegalStateException("Final turn must not include choices");
            }
            return;
        }

        if (gen.choices() == null || gen.choices().size() != 4) {
            throw new IllegalStateException("AI must return exactly 4 choices");
        }

        for (String k : List.of("A", "B", "C", "D")) {
            if (!gen.choices().containsKey(k) || gen.choices().get(k) == null || gen.choices().get(k).isBlank()) {
                throw new IllegalStateException("Missing choice " + k);
            }
        }
    }

    private void generateAndAttachImage(Turn turn, Story story) {
        try {
            // Step 1: Generate SCD from scene text
            String scdPrompt = ScdPromptFactory.createScdPrompt(turn.getSceneText(), story.getVisualStyle(), story.getVisualStyleNotes(), story.getConstitution());
            SceneCanonicalDescription scd = ai.generateScd(scdPrompt);

            // Use the safety-compliant prompt from SCD for image generation
            String imagePrompt = scd.safetyCompliantPrompt();
            if (imagePrompt == null || imagePrompt.isBlank()) {
                // Fallback to original scene text if SCD prompt is empty
                log.warn("SCD safety_compliant_prompt is empty, falling back to scene text for turn {}", turn.getId());
                imagePrompt = turn.getSceneText();
            }

            // Find previous turn if any
            byte[] prevImageBytes = null;
            ReadingSession session = turn.getSession();
            if (turn.getTurnIndex() > 1 && session != null) {
                Optional<Turn> prev = session.getTurns().stream().filter(t -> t.getTurnIndex() == turn.getTurnIndex() - 1).findFirst();

                if (prev.isPresent()) {
                    Optional<Attachment> prevAttachment = attachmentService.getAttachment(prev.get().getId(), "Turn", "TURN_IMAGE");
                    if (prevAttachment.isPresent()) {
                        prevImageBytes = fileStorageService.getBytes(prevAttachment.get().getStoragePath());
                    }
                }
            }

            String finalPrompt = SceneImagePromptFactory.fromSCD(scd, prevImageBytes != null, "Arabic");

            // Step 3: Generate Base64 (passing previous image if available for consistency)
            byte[] imageBytes = imageClient.generateImage(finalPrompt, prevImageBytes, "image/png");

            // Step 4: Upload to S3 and create Attachment record (using entityId/entityType pattern like Book)
            Long turnId = turn.getId();
            if (turnId == null) {
                throw new IllegalStateException("Turn ID is null - turn must be saved before generating image");
            }

            String key = fileStorageService.storeBytes(imageBytes, "image/png", "story-images", ".png");

            String fileName = "turn-" + turn.getTurnIndex() + "-" + turnId + ".png";

            // Check if attachment already exists (upsert pattern like Book)
            Optional<Attachment> existing = attachmentService.getAttachment(turnId, "Turn", "TURN_IMAGE");
            Attachment attachment = existing.orElseGet(() -> {
                assert session != null;
                return Attachment.builder()
                        .entityId(turnId)
                        .entityType("Turn")
                        .type("TURN_IMAGE")
                        .user(session.getReader())
                        .build();
            });

            attachment.setFileName(fileName);
            attachment.setStoragePath(key);
            attachment.setMimeType("image/png");
            attachment.setFileSize((long) imageBytes.length);
            attachment.setSourceUrl(null);

            attachmentService.save(attachment);

        } catch (Exception e) {
            log.warn("Failed to generate image for turn {}: {}", turn.getId(), e.getMessage());
            throw new RuntimeException("Gemini multimodal image generation failed", e);
        }
    }
}
