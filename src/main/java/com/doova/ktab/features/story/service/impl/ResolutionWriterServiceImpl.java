package com.doova.ktab.features.story.service.impl;

import com.doova.ktab.features.story.dto.ImageBrief;
import com.doova.ktab.features.story.dto.ResolutionResult;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.features.story.service.PromptFactory;
import com.doova.ktab.features.story.service.ResolutionWriterService;
import com.doova.ktab.features.story.service.SpringAiStoryClient;
import com.doova.ktab.features.story.util.PromptXmlParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResolutionWriterServiceImpl implements ResolutionWriterService {

    private final PromptFactory promptFactory;
    private final SpringAiStoryClient aiClient;

    @Override
    public ResolutionResult writeResolution(
            Story story,
            String storyBible,
            String finalStatsJson,
            String fullSummaryAr,
            String unpaidSetups,
            String climaxChoiceTextAr,
            String climaxRisk,
            String outcome,
            String endingVector
    ) {
        String prompt = promptFactory.resolutionWriterPrompt(
                story,
                storyBible,
                finalStatsJson,
                fullSummaryAr,
                unpaidSetups,
                climaxChoiceTextAr,
                climaxRisk,
                outcome,
                endingVector
        );

        log.info("Executing Resolution Writer for storyId={}", story.getId());
        String raw = aiClient.call(prompt);
        ParsedResolution parsed = parseResolution(raw);

        if (parsed.script == null || parsed.script.isBlank() || PromptXmlParser.hasFirstPersonMarkers(parsed.script)) {
            log.warn("Resolution Writer output invalid or contained first-person markers, retrying once...");
            String retryPrompt = prompt + "\n\nCRITICAL FIX: Output must be strictly third-person Arabic without any first-person markers (أنا، نحن، شعرت، رأيت...). Provide valid <storyboard>, <script>, <image_brief>, and <ending>.";
            raw = aiClient.call(retryPrompt);
            parsed = parseResolution(raw);
        }

        String script = (parsed.script != null && !parsed.script.isBlank())
                ? parsed.script
                : "سكنت أصوات المعركة رويداً رويداً، وتلاشت خيوط الدخان في أفق المدينة. وقف البطل متأملاً الثمن الباهظ الذي دُفع، مدركاً أن البقاء لم يكن نهاية المطاف، بل بداية لذاكرة جديدة لن تمحوها الأيام.";

        ImageBrief imageBrief = parsed.imageBrief != null ? parsed.imageBrief : new ImageBrief(
                "A quiet, contemplative final frame echoing the opening location with transformed light",
                List.of("protagonist"),
                List.of(),
                "l1",
                "dawn",
                "clear",
                "melancholy and earned peace",
                "rising dawn light"
        );

        ResolutionResult.EndingInfo ending = parsed.ending != null ? parsed.ending : new ResolutionResult.EndingInfo(
                "e1",
                "BITTERSWEET",
                "حين ينجلي الغبار، يبقى المعنى حياً في صدور أولئك الذين دفعوا الثمن."
        );

        return new ResolutionResult(parsed.storyboard, script, imageBrief, ending);
    }

    private record ParsedResolution(
            String storyboard,
            String script,
            ImageBrief imageBrief,
            ResolutionResult.EndingInfo ending
    ) {}

    private ParsedResolution parseResolution(String raw) {
        String storyboard = PromptXmlParser.extractTag(raw, "storyboard");
        String script = PromptXmlParser.cleanNarrativeScript(PromptXmlParser.extractTag(raw, "script"));
        if (script.isBlank()) {
            script = PromptXmlParser.cleanNarrativeScript(raw);
        }

        ImageBrief imageBrief = PromptXmlParser.parseJsonTag(raw, "image_brief", ImageBrief.class)
                .orElse(null);

        ResolutionResult.EndingInfo ending = PromptXmlParser.parseJsonTag(raw, "ending", ResolutionResult.EndingInfo.class)
                .orElse(null);

        return new ParsedResolution(storyboard, script, imageBrief, ending);
    }
}
