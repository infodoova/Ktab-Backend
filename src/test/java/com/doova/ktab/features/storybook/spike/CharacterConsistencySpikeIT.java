package com.doova.ktab.features.storybook.spike;

import com.doova.ktab.features.storybook.character.CharacterPrompts;
import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.CostCalculator;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.illustration.VisualQa;
import com.doova.ktab.features.storybook.illustration.VisualQaResponse;
import com.doova.ktab.features.storybook.image.GeminiImageProvider;
import com.doova.ktab.features.storybook.image.ImageGenerationException;
import com.doova.ktab.features.storybook.image.ImageRequest;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.image.ReferenceImage;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0: 5 characters x 15 scenes on each Nano Banana model. Costs real money (~$30).
 * Run: STORYBOOK_SPIKE=true GCP_PROJECT_ID=... ANTHROPIC_API_KEY=... ./mvnw test -Dtest=CharacterConsistencySpikeIT
 */
@EnabledIfEnvironmentVariable(named = "STORYBOOK_SPIKE", matches = "true")
class CharacterConsistencySpikeIT {

    private static final Path OUT = Path.of("target/storybook-spike/consistency");
    private static final int MAX_TRIES = 4; // first attempt + 3 retries, as in production

    private final ObjectMapper mapper = new ObjectMapper();
    private final StorybookProperties properties = new StorybookProperties();
    private final CostCalculator costs = new CostCalculator(properties);

    @Test
    void runConsistencySpike() throws Exception {
        GeminiImageProvider images = SpikeClients.imageProvider(properties);
        VisualQa qa = new VisualQa(SpikeClients.llm(properties), new PromptLibrary(), properties);
        byte[] styleRef = readClasspath("storybook/styles/soft_watercolor.png");

        JsonNode characters = mapper.readTree(readClasspath("storybook/spike/characters.json"));
        JsonNode scenes = mapper.readTree(readClasspath("storybook/spike/scenes.json"));
        Files.createDirectories(OUT);

        List<String> csv = new ArrayList<>(List.of(
                "model,character,page,try,qaPassed,identityMatch,strayText,anatomyOk,safe,imageCostUsd,qaCostUsd,imageLatencyMs,problems"));
        List<String> html = new ArrayList<>(List.of("<!doctype html><meta charset=utf-8><style>img{width:180px}td{vertical-align:top;font:11px sans-serif}</style><table>"));

        for (String model : List.of(properties.getImage().getPrimaryModel(), properties.getImage().getFallbackModel())) {
            int firstTryPass = 0, eventualPass = 0, pages = 0;
            BigDecimal modelCost = BigDecimal.ZERO;
            for (JsonNode c : characters) {
                String id = c.get("id").asText();
                ChildGender gender = ChildGender.valueOf(c.get("gender").asText());
                AgeBand band = AgeBand.valueOf(c.get("ageBand").asText());
                ChildAppearance appearance = mapper.treeToValue(c.get("appearance"), ChildAppearance.class);
                Path dir = OUT.resolve(model).resolve(id);
                Files.createDirectories(dir);

                ImageResult sheet = images.generate(new ImageRequest(model,
                        CharacterPrompts.sheet(gender, band, appearance), List.of(new ReferenceImage(styleRef, "image/png"))));
                Files.write(dir.resolve("sheet.png"), sheet.bytes());
                modelCost = modelCost.add(costs.imageCostUsd(model));
                List<ReferenceImage> refs = List.of(new ReferenceImage(sheet.bytes(), sheet.mimeType()),
                        new ReferenceImage(styleRef, "image/png"));

                html.add("<tr><td>" + model + "<br>" + id + "<br><img src='" + model + "/" + id + "/sheet.png'></td>");
                for (int i = 0; i < scenes.size(); i++) {
                    JsonNode s = scenes.get(i);
                    String scene = s.get("scene").asText();
                    String prompt = CharacterPrompts.scene(scene, TextZone.valueOf(s.get("zone").asText()), false, appearance.hijab());
                    pages++;
                    boolean passed = false;
                    for (int t = 1; t <= MAX_TRIES && !passed; t++) {
                        ImageResult page;
                        try {
                            page = images.generate(new ImageRequest(model, prompt, refs));
                        } catch (ImageGenerationException e) {
                            csv.add(String.join(",", model, id, String.valueOf(i + 1), String.valueOf(t),
                                    "false", "", "", "", "", "0", "0", "0", "\"" + e.getMessage().replace('"', '\'') + "\""));
                            continue;
                        }
                        String file = String.format("page-%02d-try-%d.png", i + 1, t);
                        Files.write(dir.resolve(file), page.bytes());
                        LlmCall<VisualQaResponse> check = qa.check(page.bytes(), refs, scene);
                        BigDecimal imageCost = costs.imageCostUsd(model);
                        BigDecimal qaCost = costs.llmCostUsd(check.model(), check.inputTokens(), check.outputTokens());
                        modelCost = modelCost.add(imageCost).add(qaCost);
                        VisualQaResponse v = check.value();
                        passed = v.passed();
                        if (passed && t == 1) firstTryPass++;
                        if (passed) eventualPass++;
                        csv.add(String.join(",", model, id, String.valueOf(i + 1), String.valueOf(t), String.valueOf(passed),
                                String.valueOf(v.identityMatch()), String.valueOf(v.strayText()), String.valueOf(v.anatomyOk()),
                                String.valueOf(v.safeForChildren()), imageCost.toPlainString(), qaCost.toPlainString(),
                                String.valueOf(page.latencyMs()),
                                "\"" + String.join(" | ", v.problems() == null ? List.of() : v.problems()).replace('"', '\'') + "\""));
                        html.add("<td>p" + (i + 1) + " t" + t + (passed ? " ✓" : " ✗") + "<br><img src='" + model + "/" + id + "/" + file + "'></td>");
                    }
                }
                html.add("</tr>");
            }
            System.out.printf("SPIKE %s: first-try pass %.1f%%, eventual pass %.1f%%, mean cost per 15-page book $%s%n",
                    model, 100.0 * firstTryPass / pages, 100.0 * eventualPass / pages,
                    modelCost.divide(BigDecimal.valueOf(characters.size()), 2, java.math.RoundingMode.HALF_UP));
        }
        html.add("</table>");
        Files.write(OUT.resolve("results.csv"), csv);
        Files.writeString(OUT.resolve("index.html"), String.join("\n", html));
        Files.writeString(OUT.resolve("human-scores.csv"),
                "model,character,consistency_1to5,style_stability_1to5,notes\n");
        assertThat(OUT.resolve("results.csv")).exists();
    }

    private static byte[] readClasspath(String path) throws Exception {
        try (InputStream in = CharacterConsistencySpikeIT.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + path
                        + " — the style reference is an art-director deliverable (see overview prerequisites)");
            }
            return in.readAllBytes();
        }
    }
}
