package com.doova.ktab.features.storybook.spike;

import com.doova.ktab.features.storybook.blueprint.BlueprintCatalog;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.CostCalculator;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.llm.AnthropicLlmGateway;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.story.CriticReport;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.StoryCritic;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryRequest;
import com.doova.ktab.features.storybook.story.StoryWriter;
import com.doova.ktab.features.storybook.support.StoryFixtures;
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

/**
 * Phase 0: 20 stories for the native editor. Costs ~$2.
 * Run: STORYBOOK_SPIKE=true ANTHROPIC_API_KEY=... ./mvnw test -Dtest=ArabicQualitySpikeIT
 */
@EnabledIfEnvironmentVariable(named = "STORYBOOK_SPIKE", matches = "true")
class ArabicQualitySpikeIT {

    private static final Path OUT = Path.of("target/storybook-spike/arabic");

    @Test
    void runArabicSpike() throws Exception {
        StorybookProperties properties = new StorybookProperties();
        AnthropicLlmGateway llm = SpikeClients.llm(properties);
        PromptLibrary prompts = new PromptLibrary();
        StoryWriter writer = new StoryWriter(llm, prompts);
        StoryCritic critic = new StoryCritic(llm, prompts);
        CostCalculator costs = new CostCalculator(properties);
        BlueprintCatalog catalog = new BlueprintCatalog(new ObjectMapper());

        JsonNode matrix;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("storybook/spike/arabic-matrix.json")) {
            matrix = new ObjectMapper().readTree(in);
        }
        Files.createDirectories(OUT);
        List<String> grading = new ArrayList<>(List.of(
                "story,variety,gender,ageBand,page,grammar_1to5,gender_agreement_error_y_n,tashkeel_1to5_msa_only,dialect_fidelity_1to5_dialect_only,age_fit_1to5,notes"));
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < matrix.size(); i++) {
            JsonNode row = matrix.get(i);
            StoryRequest r = new StoryRequest(row.get("name").asText(), ChildGender.valueOf(row.get("gender").asText()),
                    AgeBand.valueOf(row.get("ageBand").asText()), StoryFixtures.APPEARANCE,
                    LanguageVariety.valueOf(row.get("variety").asText()), catalog.get("first-day-of-school"),
                    row.get("pageCount").asInt(), List.of(Interest.FOOTBALL, Interest.CATS), null, StorySetting.GENERIC_CITY);

            var planCall = writer.writePlan(r);
            total = total.add(costs.llmCostUsd(planCall.model(), planCall.inputTokens(), planCall.outputTokens()));
            CriticReport first = critic.review(r, planCall.value());
            total = total.add(costs.llmCostUsd(first.llmCall().model(), first.llmCall().inputTokens(), first.llmCall().outputTokens()));

            StoryPlanResponse plan = first.plan();
            for (int pageNumber : first.failingPages()) {
                if (pageNumber == 0) continue;
                PagePlan page = plan.pages().get(pageNumber - 1);
                var rewrite = writer.rewritePage(r, page, first.problemsByPage().get(pageNumber));
                total = total.add(costs.llmCostUsd(rewrite.model(), rewrite.inputTokens(), rewrite.outputTokens()));
                plan = plan.withPage(rewrite.value());
            }
            CriticReport second = critic.review(r, plan);
            total = total.add(costs.llmCostUsd(second.llmCall().model(), second.llmCall().inputTokens(), second.llmCall().outputTokens()));

            String id = String.format("story-%02d", i + 1);
            StringBuilder md = new StringBuilder("# ").append(id).append(" — ").append(second.plan().titleAr()).append("\n\n")
                    .append("- Child: ").append(r.childNameAr()).append(", ").append(r.gender()).append(", ").append(r.ageBand())
                    .append("\n- Variety: ").append(r.variety()).append(", pages: ").append(r.pageCount()).append("\n\n");
            for (PagePlan p : second.plan().pages()) {
                md.append("**").append(p.pageNumber()).append(".** ").append(p.textAr()).append("\n\n");
                grading.add(String.join(",", id, r.variety().name(), r.gender().name(), r.ageBand().name(),
                        String.valueOf(p.pageNumber()), "", "", "", "", "", ""));
            }
            md.append("## Critic, first pass\n").append(first.problemsByPage()).append("\n\n")
              .append("## Critic, after one rewrite round\n").append(second.problemsByPage()).append('\n');
            Files.writeString(OUT.resolve(id + ".md"), md.toString());
        }
        Files.write(OUT.resolve("grading.csv"), grading);
        System.out.printf("ARABIC SPIKE: 20 stories, total LLM cost $%s%n", total.setScale(2, java.math.RoundingMode.HALF_UP));
    }
}
