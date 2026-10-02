package com.doova.ktab.features.story.service.impl;

import com.doova.ktab.features.story.dto.*;
import com.doova.ktab.features.story.enums.Beat;
import com.doova.ktab.features.story.enums.ChoiceId;
import com.doova.ktab.features.story.enums.ChoiceOutcome;
import com.doova.ktab.features.story.enums.RiskProfile;
import com.doova.ktab.features.story.enums.SessionStatus;
import com.doova.ktab.features.story.image.SceneImagePromptFactory;
import com.doova.ktab.features.story.image.VertexImageClient;
import com.doova.ktab.features.story.model.*;
import com.doova.ktab.features.story.repository.SessionRepository;
import com.doova.ktab.features.story.repository.StoryRepository;
import com.doova.ktab.features.story.repository.TurnRepository;
import com.doova.ktab.features.story.scd.SceneCanonicalDescription;
import com.doova.ktab.features.story.scd.ScdPromptFactory;
import com.doova.ktab.features.story.service.*;
import com.doova.ktab.features.story.util.JsonUtil;
import com.doova.ktab.features.story.util.PromptXmlParser;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorySessionServiceImpl implements StorySessionService {

    private final StoryRepository storyRepository;
    private final SessionRepository sessionRepo;
    private final TurnRepository turnRepository;
    private final SpringAiStoryClient ai;
    private final PromptFactory prompts;
    private final SessionStateFactory sessionStateFactory;
    private final StoryArchitectService storyArchitectService;
    private final SceneEngineService sceneEngineService;
    private final VisualDirectorService visualDirectorService;
    private final ResolutionWriterService resolutionWriterService;
    private final VertexImageClient imageClient;
    private final FileStorageService fileStorageService;
    private final AttachmentService attachmentService;
    private final java.util.concurrent.ConcurrentHashMap<String, Object> sessionLocks = new java.util.concurrent.ConcurrentHashMap<>();

    @Value("${app.story.maxSceneWords:220}")
    private int maxSceneWords;
    @Value("${app.story.summarizeEveryTurns:4}")
    private int summarizeEveryTurns;
    @Value("${app.story.maxScenesDefault:10}")
    private int maxScenesDefault;

    @Override
    @Transactional
    public List<Turn> startSession(Long storyId, User reader) {
        String lockKey = "start:" + storyId + ":" + reader.getId();
        Object lock = sessionLocks.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            try {
                Story story = storyRepository.findById(storyId)
                        .orElseThrow(() -> new IllegalArgumentException("Story not found"));

                var existingSessions = sessionRepo.findByStoryIdAndReaderIdOrderByIdDesc(storyId, reader.getId());
                if (!existingSessions.isEmpty()) {
                    ReadingSession session = existingSessions.getFirst();
                    if (existingSessions.size() > 1) {
                        log.warn("Multiple reading sessions found for storyId={} readerId={}; reusing newest sessionId={}",
                                storyId, reader.getId(), session.getId());
                    }
                    return session.getTurns().stream().sorted(Comparator.comparingInt(Turn::getTurnIndex).reversed()).toList();
                }

            int maxScenes = effectiveMaxScenes(story);
            List<BeatEntry> beatMap = BeatMapper.buildFullBeatMap(maxScenes);

            // Ensure Story Bible exists
            String storyBible = ensureStoryBible(story, beatMap);

            SessionState initialState = sessionStateFactory.initialStateFor(story);
            ReadingSession session = new ReadingSession(story, reader, initialState);
            session.setStory(story);

            // Scene 1 (HOOK)
            BeatEntry beatEntry = beatMap.getFirst();
            String statsJson = serializeState(initialState);
            String scenePlanEntryJson = extractScenePlanEntry(storyBible, 1);

            SceneEngineResult sceneResult = sceneEngineService.generateScene(
                    story,
                    storyBible,
                    1,
                    maxScenes,
                    beatEntry.beat(),
                    beatEntry.tension(),
                    scenePlanEntryJson,
                    statsJson,
                    "",
                    "[]",
                    "[]",
                    "[]",
                    "[]",
                    "none",
                    "none",
                    "none",
                    "none",
                    "SUCCESS",
                    "none"
            );

            // Direct image for Scene 1
            VisualDirectorOutput directorOutput = visualDirectorService.directImage(
                    storyBible,
                    story.getVisualStyle() != null ? story.getVisualStyle().name() : "Cinematic Oil Painting",
                    story.getVisualStyleNotes(),
                    null,
                    sceneResult.imageBrief(),
                    beatEntry.beat(),
                    beatEntry.tension(),
                    1,
                    maxScenes
            );

            String choicesJson = buildChoicesJsonForFrontend(sceneResult.choices());
            String choicesMetaJson = JsonUtil.write(sceneResult.choices());
            String imageBriefJson = JsonUtil.write(sceneResult.imageBrief());

            Turn t1 = new Turn(session, 1, sceneResult.script(), choicesJson);
            t1.setStoryboard(sceneResult.storyboard());
            t1.setImageBrief(imageBriefJson);
            t1.setChoicesMetaJson(choicesMetaJson);

            session.addTurn(t1);
            session.setCurrentRiskMapping(extractRiskMapping(sceneResult.choices()));
            if (sceneResult.stateUpdate() != null && sceneResult.stateUpdate().summary_append_ar() != null) {
                session.setRollingSummary(sceneResult.stateUpdate().summary_append_ar());
            }

            if (1 >= maxScenes) {
                session.setStatus(SessionStatus.COMPLETED);
            }
            session = sessionRepo.saveAndFlush(session);
            t1 = turnRepository.saveAndFlush(t1);
            session.addTurn(t1);

            try {
                generateAndAttachImageV2(t1, story, directorOutput);
            } catch (Exception e) {
                session.removeTurn(t1);
                turnRepository.delete(t1);
                throw e;
            }

            return List.of(t1);
        } catch (Exception e) {
            log.error("Failed to start story session: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
        }
    }

    @Override
    @Transactional
    public List<Turn> chooseAndGenerateNext(Long sessionId, String choiceRaw) {
        return chooseAndGenerateNext(sessionId, choiceRaw, null);
    }

    @Override
    @Transactional
    public List<Turn> chooseAndGenerateNext(Long sessionId, String choiceRaw, Integer turnIndex) {
        String lockKey = "choose:" + sessionId;
        Object lock = sessionLocks.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            try {
                ChoiceId choice = ChoiceId.from(choiceRaw);

                ReadingSession session = sessionRepo.findById(sessionId)
                        .orElseThrow(() -> new IllegalArgumentException("Session not found"));
                if (session.getStatus() != SessionStatus.ACTIVE) {
                    throw new IllegalStateException("Session is not active");
                }

                Story story = session.getStory();
                int maxScenes = effectiveMaxScenes(story);
                List<BeatEntry> beatMap = BeatMapper.buildFullBeatMap(maxScenes);
                String storyBible = ensureStoryBible(story, beatMap);

                Turn current = session.getTurns().stream()
                        .max(Comparator.comparingInt(Turn::getTurnIndex))
                        .orElseThrow(() -> new IllegalStateException("No turns for session"));

                // Idempotency check 1: explicit turnIndex already processed
                if (turnIndex != null && current.getTurnIndex() > turnIndex) {
                    log.info("Turn {} already processed for sessionId={}, returning existing turns", turnIndex, sessionId);
                    return session.getTurns().stream().sorted(Comparator.comparingInt(Turn::getTurnIndex).reversed()).toList();
                }

                // Idempotency check 2: rapid duplicate click / script retry debounce
                if (session.getTurns().size() > 1 && current.getCreatedAt() != null) {
                    long secondsSinceCreation = Math.abs(java.time.Duration.between(current.getCreatedAt(), java.time.LocalDateTime.now()).getSeconds());
                    if (secondsSinceCreation < 6) {
                        Turn previousTurn = session.getTurns().stream()
                                .filter(t -> t.getTurnIndex() == current.getTurnIndex() - 1)
                                .findFirst()
                                .orElse(null);
                        if (previousTurn != null && choice.name().equalsIgnoreCase(previousTurn.getChosenChoiceId())) {
                            log.info("Debouncing duplicate choice '{}' on sessionId={} (Turn {} created {}s ago); returning current turns",
                                    choice.name(), sessionId, current.getTurnIndex(), secondsSinceCreation);
                            return session.getTurns().stream().sorted(Comparator.comparingInt(Turn::getTurnIndex).reversed()).toList();
                        }
                    }
                }

                if (current.getTurnIndex() >= maxScenes) {
                    session.setStatus(SessionStatus.COMPLETED);
                    throw new IllegalStateException("Reached max scenes for this story");
                }

            // Find chosen choice from rich meta
            List<ChoiceV2> choicesMeta = parseChoicesMeta(current.getChoicesMetaJson());
            ChoiceV2 chosenChoice = findChosenChoice(choicesMeta, choice.name(), current.getChoicesJson());

            // Roll outcome for previous choice
            ChoiceOutcome outcome = rollOutcome(chosenChoice.risk_profile());
            String consequenceSeed = (outcome == ChoiceOutcome.SUCCESS)
                    ? chosenChoice.on_success_en()
                    : chosenChoice.on_failure_en();

            // Apply choice effect to session state
            SessionState updatedState = ChoiceEffectResolver.apply(session.getState(), choice);
            session.setState(updatedState);
            current.setChosenChoiceId(choice.name());

            int nextTurnIndex = current.getTurnIndex() + 1;
            boolean isFinalTurn = (nextTurnIndex >= maxScenes);

            Turn nextTurn;
            VisualDirectorOutput directorOutput;

            if (isFinalTurn) {
                // =============================================================
                // TURN N: RESOLUTION WRITER
                // =============================================================
                log.info("Generating Final Resolution Turn {} for sessionId={}", nextTurnIndex, sessionId);
                ResolutionResult resResult = resolutionWriterService.writeResolution(
                        story,
                        storyBible,
                        serializeState(session.getState()),
                        session.getRollingSummary(),
                        session.getUnpaidSetups(),
                        chosenChoice.text_ar(),
                        chosenChoice.risk_profile(),
                        outcome.name(),
                        chosenChoice.ending_vector()
                );

                Beat finalBeat = Beat.RESOLUTION;
                int finalTension = 5;

                directorOutput = visualDirectorService.directImage(
                        storyBible,
                        story.getVisualStyle() != null ? story.getVisualStyle().name() : "Cinematic Oil Painting",
                        story.getVisualStyleNotes(),
                        null,
                        resResult.imageBrief(),
                        finalBeat,
                        finalTension,
                        nextTurnIndex,
                        maxScenes
                );

                nextTurn = new Turn(session, nextTurnIndex, resResult.script(), "{}");
                nextTurn.setStoryboard(resResult.storyboard());
                nextTurn.setImageBrief(JsonUtil.write(resResult.imageBrief()));
                nextTurn.setChoicesMetaJson("{}");
                session.setStatus(SessionStatus.COMPLETED);

            } else {
                // =============================================================
                // TURNS 2 .. N-1: SCENE ENGINE
                // =============================================================
                BeatEntry beatEntry = beatMap.get(nextTurnIndex - 1);
                String scenePlanEntryJson = extractScenePlanEntry(storyBible, nextTurnIndex);

                SceneEngineResult sceneResult = sceneEngineService.generateScene(
                        story,
                        storyBible,
                        nextTurnIndex,
                        maxScenes,
                        beatEntry.beat(),
                        beatEntry.tension(),
                        scenePlanEntryJson,
                        serializeState(session.getState()),
                        session.getRollingSummary(),
                        session.getOpenThreads(),
                        session.getUnpaidSetups(),
                        session.getInventory(),
                        session.getCharacterStatus(),
                        session.getCurrentRiskMapping(),
                        chosenChoice.text_ar(),
                        chosenChoice.archetype(),
                        chosenChoice.risk_profile(),
                        outcome.name(),
                        consequenceSeed
                );

                directorOutput = visualDirectorService.directImage(
                        storyBible,
                        story.getVisualStyle() != null ? story.getVisualStyle().name() : "Cinematic Oil Painting",
                        story.getVisualStyleNotes(),
                        null,
                        sceneResult.imageBrief(),
                        beatEntry.beat(),
                        beatEntry.tension(),
                        nextTurnIndex,
                        maxScenes
                );

                String choicesJson = buildChoicesJsonForFrontend(sceneResult.choices());
                String choicesMetaJson = JsonUtil.write(sceneResult.choices());
                String imageBriefJson = JsonUtil.write(sceneResult.imageBrief());

                nextTurn = new Turn(session, nextTurnIndex, sceneResult.script(), choicesJson);
                nextTurn.setStoryboard(sceneResult.storyboard());
                nextTurn.setImageBrief(imageBriefJson);
                nextTurn.setChoicesMetaJson(choicesMetaJson);

                session.setCurrentRiskMapping(extractRiskMapping(sceneResult.choices()));
                updateSessionStateMeta(session, sceneResult.stateUpdate());
            }

            session = sessionRepo.saveAndFlush(session);
            nextTurn = turnRepository.saveAndFlush(nextTurn);
            session.addTurn(nextTurn);

            try {
                generateAndAttachImageV2(nextTurn, story, directorOutput);
            } catch (Exception e) {
                session.removeTurn(nextTurn);
                turnRepository.delete(nextTurn);
                throw e;
            }

            maybeSummarize(session, story);

            return session.getTurns().stream()
                    .sorted(Comparator.comparingInt(Turn::getTurnIndex).reversed())
                    .toList();
        } catch (Exception e) {
            log.error("Failed to generate next story turn: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
        }
    }

    private ChoiceOutcome rollOutcome(String riskProfile) {
        RiskProfile risk = RiskProfile.fromSafe(riskProfile);
        double roll = Math.random();
        return switch (risk) {
            case SAFE -> ChoiceOutcome.SUCCESS;
            case STEADY -> (roll < 0.85) ? ChoiceOutcome.SUCCESS : ChoiceOutcome.PARTIAL;
            case PERIL -> (roll < 0.55) ? ChoiceOutcome.SUCCESS : ChoiceOutcome.FAILURE;
            case GAMBLE -> (roll < 0.45) ? ChoiceOutcome.SUCCESS : ChoiceOutcome.FAILURE;
        };
    }

    private List<ChoiceV2> parseChoicesMeta(String choicesMetaJson) {
        if (choicesMetaJson == null || choicesMetaJson.isBlank() || choicesMetaJson.equals("{}")) {
            return Collections.emptyList();
        }
        return PromptXmlParser.parseJsonListTag(choicesMetaJson, "", new TypeReference<List<ChoiceV2>>() {})
                .orElse(Collections.emptyList());
    }

    private ChoiceV2 findChosenChoice(List<ChoiceV2> choicesMeta, String chosenLetter, String fallbackChoicesJson) {
        for (ChoiceV2 c : choicesMeta) {
            if (c.id() != null && c.id().equalsIgnoreCase(chosenLetter)) {
                return c;
            }
        }
        // Fallback if metadata is missing
        String text = chosenLetter;
        if (fallbackChoicesJson != null) {
            Map<?, ?> map = JsonUtil.read(fallbackChoicesJson, Map.class);
            if (map != null && map.get(chosenLetter) != null) {
                text = map.get(chosenLetter).toString();
            }
        }
        return new ChoiceV2(
                chosenLetter,
                com.doova.ktab.features.story.enums.ChoiceArchetype.forLetter(chosenLetter).name(),
                text,
                "STEADY",
                3,
                3,
                Map.of(),
                "The action is carried out.",
                "Complications arise.",
                null
        );
    }

    private void updateSessionStateMeta(ReadingSession session, StateUpdate stateUpdate) {
        if (stateUpdate == null) return;
        if (stateUpdate.summary_append_ar() != null && !stateUpdate.summary_append_ar().isBlank()) {
            String current = session.getRollingSummary();
            if (current == null || current.isBlank()) {
                session.setRollingSummary(stateUpdate.summary_append_ar());
            } else {
                session.setRollingSummary(current + "\n" + stateUpdate.summary_append_ar());
            }
        }
        if (stateUpdate.threads_opened() != null) {
            session.setOpenThreads(JsonUtil.write(stateUpdate.threads_opened()));
        }
        if (stateUpdate.setups_planted() != null) {
            session.setUnpaidSetups(JsonUtil.write(stateUpdate.setups_planted()));
        }
        if (stateUpdate.inventory_changes() != null) {
            session.setInventory(JsonUtil.write(stateUpdate.inventory_changes()));
        }
        if (stateUpdate.character_changes() != null) {
            session.setCharacterStatus(JsonUtil.write(stateUpdate.character_changes()));
        }
    }

    private String buildChoicesJsonForFrontend(List<ChoiceV2> choices) {
        if (choices == null || choices.isEmpty()) {
            return "{}";
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (ChoiceV2 c : choices) {
            map.put(c.id(), c.text_ar());
        }
        return JsonUtil.write(map);
    }

    private String extractRiskMapping(List<ChoiceV2> choices) {
        if (choices == null || choices.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ChoiceV2 c : choices) {
            sb.append(c.id()).append(":").append(c.risk_profile()).append(" ");
        }
        return sb.toString().trim();
    }

    private String ensureStoryBible(Story story, List<BeatEntry> beatMap) {
        if (story.getStoryBible() != null && !story.getStoryBible().isBlank()) {
            return story.getStoryBible();
        }
        log.info("Story Bible missing for storyId={}, generating now...", story.getId());
        String bible = storyArchitectService.generateStoryBible(story, beatMap);
        story.setStoryBible(bible);
        storyRepository.save(story);
        return bible;
    }

    private String extractScenePlanEntry(String storyBible, int sceneIndex) {
        if (storyBible == null || storyBible.isBlank()) return "{}";
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(storyBible);
            var planNode = node.path("scene_plan");
            if (planNode.isArray()) {
                for (var elem : planNode) {
                    if (elem.path("scene").asInt() == sceneIndex) {
                        return elem.toString();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse scene plan for scene {}: {}", sceneIndex, e.getMessage());
        }
        return "{}";
    }

    private String serializeState(SessionState state) {
        if (state == null) return "{}";
        Map<String, Object> map = new LinkedHashMap<>();
        if (state instanceof SurvivalState surv) {
            map.put("stamina", surv.stamina());
            map.put("hunger", surv.hunger());
        } else if (state instanceof PsychologicalState psy) {
            map.put("anxiety", psy.anxiety());
            map.put("attachment", psy.attachment());
        } else if (state instanceof PoliticalState pol) {
            map.put("visibility", pol.visibility().name());
            map.put("publicTrust", pol.trustWithPeople());
            map.put("authorityTrust", pol.trustWithAuthority());
            map.put("moralWeight", pol.moralWeight().name());
        } else if (state instanceof MoralState mor) {
            map.put("integrity", mor.integrity());
            map.put("guilt", mor.guilt());
            map.put("corruption", mor.integrity() < 50 ? "CORRUPTED" : (mor.integrity() < 80 ? "COMPROMISED" : "CLEAN"));
        }
        return JsonUtil.write(map);
    }

    private void generateAndAttachImageV2(Turn turn, Story story, VisualDirectorOutput directorOutput) {
        try {
            Long turnId = turn.getId();
            if (turnId == null) {
                throw new IllegalStateException("Turn ID is null - turn must be saved before generating image");
            }

            // CRITICAL: Prevent duplicate illustration generation if already exists
            Optional<Attachment> existing = attachmentService.getAttachment(turnId, "Turn", "TURN_IMAGE");
            if (existing.isPresent() && existing.get().getStoragePath() != null && !existing.get().getStoragePath().isBlank()) {
                log.info("Turn {} already has an illustration attached ({}); skipping duplicate generation", turnId, existing.get().getStoragePath());
                return;
            }

            String finalPrompt;
            if (directorOutput != null && directorOutput.prompt() != null && !directorOutput.prompt().isBlank()) {
                finalPrompt = directorOutput.prompt();
                if (directorOutput.negative_prompt() != null && !directorOutput.negative_prompt().isBlank()) {
                    finalPrompt += "\nNegative prompt: " + directorOutput.negative_prompt();
                }
            } else {
                String scdPrompt = ScdPromptFactory.createScdPrompt(turn.getSceneText(), story.getVisualStyle(), story.getVisualStyleNotes(), story.getConstitution());
                SceneCanonicalDescription scd = ai.generateScd(scdPrompt);
                finalPrompt = SceneImagePromptFactory.fromSCD(scd, false, "Arabic");
            }

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

            byte[] imageBytes = imageClient.generateImage(finalPrompt, prevImageBytes, "image/png");

            String key = fileStorageService.storeBytes(imageBytes, "image/png", "story-images", ".png");
            String fileName = "turn-" + turn.getTurnIndex() + "-" + turnId + ".png";

            Attachment attachment = existing.orElseGet(() -> Attachment.builder()
                    .entityId(turnId)
                    .entityType("Turn")
                    .type("TURN_IMAGE")
                    .user(session.getReader())
                    .build());

            attachment.setFileName(fileName);
            attachment.setStoragePath(key);
            attachment.setMimeType("image/png");
            attachment.setFileSize((long) imageBytes.length);
            attachment.setSourceUrl(null);

            attachmentService.save(attachment);
        } catch (Exception e) {
            log.warn("Failed to generate image for turn {}: {}", turn.getId(), e.getMessage());
            throw new RuntimeException("Multimodal image generation failed", e);
        }
    }

    private void maybeSummarize(ReadingSession session, Story story) {
        int lastSumm = session.getLastSummarizedTurnIndex();
        int end = lastSumm + summarizeEveryTurns;
        List<Turn> allTurns = session.getTurns().stream().sorted(Comparator.comparingInt(Turn::getTurnIndex)).toList();

        if (allTurns.isEmpty()) return;

        int maxTurnIndex = allTurns.getLast().getTurnIndex();
        if (maxTurnIndex < end) return;

        List<Turn> batch = allTurns.stream().filter(t -> t.getTurnIndex() > lastSumm && t.getTurnIndex() <= end && !t.isSummarized()).toList();
        if (batch.size() < summarizeEveryTurns) return;

        List<java.util.Map<String, Object>> turnData = batch.stream().map(t -> {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("turnIndex", t.getTurnIndex());
            map.put("sceneText", t.getSceneText() != null ? t.getSceneText() : "");
            map.put("chosenChoiceId", t.getChosenChoiceId() != null ? t.getChosenChoiceId() : "none");
            return map;
        }).toList();

        String sys = prompts.summarizerSystem(story);
        String user = "Turns to summarize: " + JsonUtil.write(turnData);

        MemorySummary summary = ai.summarize(sys, user);
        session.setRollingSummary(JsonUtil.write(summary));
        session.setLastSummarizedTurnIndex(end);
        batch.forEach(t -> t.setSummarized(true));
    }

    private int effectiveMaxScenes(Story story) {
        if (story.getSceneCount() >= 5 && story.getSceneCount() <= 15) {
            return story.getSceneCount();
        }
        return Math.max(5, Math.min(15, story.getSceneCount() > 0 ? story.getSceneCount() : maxScenesDefault));
    }
}
