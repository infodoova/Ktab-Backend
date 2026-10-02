package com.doova.ktab.features.story.service.impl;

import com.doova.ktab.features.story.dto.ChoiceV2;
import com.doova.ktab.features.story.dto.ImageBrief;
import com.doova.ktab.features.story.dto.SceneEngineResult;
import com.doova.ktab.features.story.dto.StateUpdate;
import com.doova.ktab.features.story.enums.Beat;
import com.doova.ktab.features.story.enums.ChoiceArchetype;
import com.doova.ktab.features.story.enums.RiskProfile;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.features.story.service.BeatMapper;
import com.doova.ktab.features.story.service.PromptFactory;
import com.doova.ktab.features.story.service.SceneEngineService;
import com.doova.ktab.features.story.service.SpringAiStoryClient;
import com.doova.ktab.features.story.util.PromptXmlParser;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SceneEngineServiceImpl implements SceneEngineService {

    private final PromptFactory promptFactory;
    private final SpringAiStoryClient aiClient;

    private static final List<String> REQUIRED_LETTERS = List.of("A", "B", "C", "D");
    private static final Set<String> ALL_RISK_PROFILES = Set.of("GAMBLE", "STEADY", "SAFE", "PERIL");

    @Override
    public SceneEngineResult generateScene(
            Story story,
            String storyBible,
            int sceneIndex,
            int totalScenes,
            Beat beat,
            int tension,
            String scenePlanEntryJson,
            String statsJson,
            String summarySoFarAr,
            String openThreads,
            String unpaidSetups,
            String inventory,
            String characterStatus,
            String prevRiskMapping,
            String prevChoiceTextAr,
            String prevArchetype,
            String prevRisk,
            String outcome,
            String prevSeedEn
    ) {
        String beatInstructions = BeatMapper.getBeatInstructions(beat);

        String prompt = promptFactory.sceneEnginePrompt(
                story,
                storyBible,
                sceneIndex,
                totalScenes,
                beat.name(),
                tension,
                scenePlanEntryJson,
                beatInstructions,
                statsJson,
                summarySoFarAr,
                openThreads,
                unpaidSetups,
                inventory,
                characterStatus,
                prevRiskMapping,
                prevChoiceTextAr,
                prevArchetype,
                prevRisk,
                outcome,
                prevSeedEn
        );

        log.info("Executing Scene Engine for scene {}/{} (Beat: {}, Tension: {})", sceneIndex, totalScenes, beat, tension);
        String raw = aiClient.call(prompt);
        ParsedScene parsed = parseScene(raw);

        String validationError = validateScene(parsed, beat, prevRiskMapping);
        if (validationError != null) {
            log.warn("Scene Engine output failed validation: {}. Retrying once with error feedback...", validationError);
            String retryPrompt = prompt + "\n\nCRITICAL FIX REQUIRED: Previous attempt was rejected because:\n- " +
                    validationError + "\nPlease regenerate strictly obeying all rules, outputting <storyboard>, <script>, <choices>, <image_brief>, <state_update>.";
            raw = aiClient.call(retryPrompt);
            parsed = parseScene(raw);
        }

        // Apply corrective normalization to guarantee safety and valid contract
        List<ChoiceV2> normalizedChoices = normalizeChoices(parsed.choices, prevRiskMapping);
        String script = (parsed.script != null && !parsed.script.isBlank())
                ? parsed.script
                : "تقدّم البطل عبر الأزقة الصامتة مترقباً خطوات الحراس. تلاقت النظرات واشتد التوتر في الهواء.";

        ImageBrief imageBrief = parsed.imageBrief != null ? parsed.imageBrief : new ImageBrief(
                "A tense dramatic standoff in the historical setting",
                List.of("protagonist"),
                List.of(),
                "l1",
                "night",
                "foggy",
                "tense",
                "torchlight"
        );

        StateUpdate stateUpdate = parsed.stateUpdate != null ? parsed.stateUpdate : new StateUpdate(
                "تغيرت مجريات الأحداث وازدادت وطأة المواجهة.",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        return new SceneEngineResult(parsed.storyboard, script, normalizedChoices, imageBrief, stateUpdate);
    }

    private record ParsedScene(
            String storyboard,
            String script,
            List<ChoiceV2> choices,
            ImageBrief imageBrief,
            StateUpdate stateUpdate
    ) {}

    private ParsedScene parseScene(String raw) {
        String storyboard = PromptXmlParser.extractTag(raw, "storyboard");
        String script = PromptXmlParser.cleanNarrativeScript(PromptXmlParser.extractTag(raw, "script"));
        if (script.isBlank()) {
            script = PromptXmlParser.cleanNarrativeScript(raw);
        }

        List<ChoiceV2> choices = PromptXmlParser.parseJsonListTag(raw, "choices", new TypeReference<List<ChoiceV2>>() {})
                .orElse(Collections.emptyList());

        ImageBrief imageBrief = PromptXmlParser.parseJsonTag(raw, "image_brief", ImageBrief.class)
                .orElse(null);

        StateUpdate stateUpdate = PromptXmlParser.parseJsonTag(raw, "state_update", StateUpdate.class)
                .orElse(null);

        return new ParsedScene(storyboard, script, choices, imageBrief, stateUpdate);
    }

    private String validateScene(ParsedScene parsed, Beat beat, String prevRiskMapping) {
        if (parsed.script == null || parsed.script.isBlank()) {
            return "Missing or empty <script>";
        }

        if (parsed.script.contains("<storyboard>") || parsed.script.contains("<choices>") || parsed.script.contains("<image_brief>")) {
            return "Script contains unparsed XML tags";
        }

        if (PromptXmlParser.hasFirstPersonMarkers(parsed.script)) {
            return "Script contains forbidden first-person perspective markers";
        }

        if (parsed.choices == null || parsed.choices.size() != 4) {
            return "Expected exactly 4 choices in <choices>, got " + (parsed.choices == null ? 0 : parsed.choices.size());
        }

        Set<String> seenLetters = new HashSet<>();
        Set<String> seenRisks = new HashSet<>();
        StringBuilder currentMapping = new StringBuilder();

        for (ChoiceV2 choice : parsed.choices) {
            if (choice.id() == null) return "Choice missing id";
            String id = choice.id().toUpperCase().trim();
            seenLetters.add(id);

            String expectedArch = ChoiceArchetype.forLetter(id).name();
            if (choice.archetype() != null && !choice.archetype().equalsIgnoreCase(expectedArch)) {
                return "Choice " + id + " archetype must be " + expectedArch;
            }

            if (choice.risk_profile() == null) return "Choice " + id + " missing risk_profile";
            String risk = choice.risk_profile().toUpperCase().trim();
            seenRisks.add(risk);
            currentMapping.append(id).append(":").append(risk).append(" ");
        }

        if (!seenLetters.containsAll(REQUIRED_LETTERS)) {
            return "Choices must contain exactly IDs A, B, C, D";
        }

        if (!seenRisks.containsAll(ALL_RISK_PROFILES)) {
            return "All four risk profiles (GAMBLE, STEADY, SAFE, PERIL) must be present once";
        }

        if (prevRiskMapping != null && !prevRiskMapping.isBlank() && !prevRiskMapping.equalsIgnoreCase("none")) {
            if (currentMapping.toString().trim().equalsIgnoreCase(prevRiskMapping.trim())) {
                return "Risk mapping must differ from previous scene mapping";
            }
        }

        if (beat == Beat.CLIMAX) {
            Set<String> endingVectors = new HashSet<>();
            for (ChoiceV2 choice : parsed.choices) {
                if (choice.ending_vector() != null) {
                    endingVectors.add(choice.ending_vector().trim());
                }
            }
            if (endingVectors.size() <= 1) {
                return "At CLIMAX, choices must point toward distinct ending_vectors";
            }
        }

        return null;
    }

    private List<ChoiceV2> normalizeChoices(List<ChoiceV2> choices, String prevRiskMapping) {
        Map<String, ChoiceV2> map = new LinkedHashMap<>();
        if (choices != null) {
            for (ChoiceV2 c : choices) {
                if (c.id() != null) {
                    map.put(c.id().trim().toUpperCase(), c);
                }
            }
        }

        List<ChoiceV2> result = new ArrayList<>(4);
        Set<String> usedRisks = new HashSet<>();
        List<String> availableRisks = new ArrayList<>(ALL_RISK_PROFILES);
        Collections.shuffle(availableRisks);

        for (String letter : REQUIRED_LETTERS) {
            ChoiceV2 existing = map.get(letter);
            String archetype = ChoiceArchetype.forLetter(letter).name();
            String text = (existing != null && existing.text_ar() != null && !existing.text_ar().isBlank())
                    ? existing.text_ar()
                    : defaultChoiceText(letter);

            String risk = (existing != null && existing.risk_profile() != null)
                    ? existing.risk_profile().toUpperCase().trim()
                    : null;

            if (risk == null || !ALL_RISK_PROFILES.contains(risk) || usedRisks.contains(risk)) {
                // Pick an unused risk profile
                for (String r : availableRisks) {
                    if (!usedRisks.contains(r)) {
                        risk = r;
                        break;
                    }
                }
            }
            if (risk == null) risk = "STEADY";
            usedRisks.add(risk);

            Map<String, Object> effects = (existing != null && existing.effects() != null) ? existing.effects() : Map.of();
            String onSuccess = (existing != null && existing.on_success_en() != null) ? existing.on_success_en() : "The action succeeds with tangible impact.";
            String onFailure = (existing != null && existing.on_failure_en() != null) ? existing.on_failure_en() : "Complications arise from the attempt.";
            String endingVector = (existing != null) ? existing.ending_vector() : null;

            result.add(new ChoiceV2(
                    letter,
                    archetype,
                    text,
                    risk,
                    existing != null && existing.risk_level() != null ? existing.risk_level() : 3,
                    existing != null && existing.reward_level() != null ? existing.reward_level() : 3,
                    effects,
                    onSuccess,
                    onFailure,
                    endingVector
            ));
        }

        return result;
    }

    private String defaultChoiceText(String letter) {
        return switch (letter) {
            case "A" -> "يواجه التحدي مباشرة بحزم وإصرار.";
            case "B" -> "يحمي رفيقه ويبحث عن مخرج آمن.";
            case "C" -> "يتفاوض بحذر للوصول إلى تسوية مناسبة.";
            case "D" -> "يتراجع بحذر متفادياً لفت الانتباه.";
            default -> "يتحرك بحذر لمواجهة الموقف.";
        };
    }
}
