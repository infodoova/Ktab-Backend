# Storybook 01 — AI Core and Phase 0 Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Read `2026-09-24-storybook-00-overview.md` first — its "Shared contracts" and "Global Constraints" sections apply to every task here.

**Goal:** Build every AI-facing component of the storybook feature as pure, database-free Java — Claude gateway, Gemini image provider, cost calculator, blueprints, prompts, Arabic text checks, story writer, critic, moderation and visual QA — and two Phase 0 spike harnesses that run that exact code against the real models so the team can make the go/no-go decision.

**Architecture:** Everything lives under `com.doova.ktab.features.storybook` and depends only on two interfaces: `LlmGateway` (implemented with the official Anthropic Java SDK) and `ImageProvider` (implemented with Ktab's existing `com.google.genai.Client` bean). Services take plain records in and return records out; no JPA, no web layer. Prompts and dialect guides are classpath text files so editors can change them without touching Java.

**Tech Stack:** Java 21, Spring Boot 3.5.7, `com.anthropic:anthropic-java` 2.34.0, `com.google.genai:google-genai` (upgraded from 1.3.0), Jackson, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-24-personalized-storybook-spec.md`

## Global Constraints

See the overview. The ones this sub-plan touches most:

- Claude model `claude-sonnet-5`; image models `gemini-3.1-flash-image` (primary) and `gemini-3-pro-image` (fallback).
- Images: 2K, square (`1:1`), text-free, with a TOP or BOTTOM zone left empty for text.
- 1–4 sentences per page; words per page by age band (3–5 → 25, 6–8 → 45, 9–10 → 70, decision D2).
- MSA is written fully vocalized; dialects are written with no tashkeel and follow their style guide.
- The child's name is printed exactly as the parent typed it.
- Gender agreement is checked on every page.
- Image safety filters must block at `BLOCK_LOW_AND_ABOVE`; never copy `VertexImageClient`'s `OFF` settings.

## Review Focus

Owned by this sub-plan (from the overview): **#1 name typed with tashkeel or a different alif form.** Test: Task 9, `NameEnforcerTest` and `DeterministicTextChecksTest`.

Also covered here because they only show up with real inputs:

- A 2048 px PNG is often larger than Claude's per-image limit (5 MB). Expected: QA downsizes to a 1024 px JPEG before sending. Test: Task 12, `ImageDownscalerTest`.
- The LLM returns 14 pages when 15 were asked for, or numbers them 0–14. Expected: rejected as a retryable failure, never stored. Test: Task 8, `StoryWriterTest.rejectsWrongPageCount`.

## File structure

```
src/main/java/com/doova/ktab/features/storybook/
├── config/
│   ├── StorybookProperties.java          # ktab.storybook.* (Task 2)
│   └── AnthropicClientConfig.java        # AnthropicClient bean (Task 4)
├── enums/
│   ├── AgeBand.java  ChildGender.java  LanguageVariety.java  TashkeelLevel.java
│   ├── TextZone.java  LlmPurpose.java  Interest.java  StorySetting.java   (Task 2)
├── llm/
│   ├── LlmGateway.java  LlmRequest.java  LlmImage.java  LlmCall.java
│   ├── LlmCallFailedException.java  AnthropicLlmGateway.java            (Task 4)
├── cost/CostCalculator.java                                             (Task 3)
├── image/
│   ├── ImageProvider.java  ImageRequest.java  ReferenceImage.java  ImageResult.java
│   ├── ImageGenerationException.java  GeminiImageProvider.java          (Task 5)
│   └── ImageDownscaler.java                                             (Task 12)
├── prompt/PromptLibrary.java                                            (Task 6)
├── blueprint/Blueprint.java  BlueprintBeat.java  BlueprintCatalog.java  (Task 7)
├── character/ChildAppearance.java  CompanionSpec.java  CharacterPrompts.java (Tasks 8, 12)
├── story/
│   ├── StoryRequest.java  StoryPlanResponse.java  PagePlan.java  CharacterInScene.java
│   ├── StoryPromptBuilder.java  StoryWriter.java  StoryPlanInvalidException.java (Task 8)
│   ├── TashkeelFilter.java  ArabicText.java  NameEnforcer.java  DeterministicTextChecks.java (Task 9)
│   ├── CriticResponse.java  PageVerdict.java  CriticReport.java  StoryCritic.java  (Task 10)
│   └── ModerationResponse.java  ModerationService.java                  (Task 11)
└── illustration/VisualQaResponse.java  VisualQa.java                    (Task 12)

src/main/resources/storybook/
├── prompts/  story-plan-system.md  page-rewrite-system.md  critic-system.md
│             moderation-system.md  visual-qa-system.md                   (Task 6)
├── dialects/ lebanese.md  egyptian.md  gulf.md                           (Task 6)
├── blueprints/first-day-of-school.v1.json                                (Task 7)
└── styles/soft_watercolor.png            # art director deliverable, see overview

src/test/java/com/doova/ktab/features/storybook/
├── support/FakeLlmGateway.java  StoryFixtures.java                       (Tasks 8, 10)
├── (one *Test.java per component above)
└── spike/SpikeClients.java  CharacterConsistencySpikeIT.java  ArabicQualitySpikeIT.java (Tasks 13, 14)
src/test/resources/storybook/spike/characters.json  scenes.json  arabic-matrix.json

docs/storybook/phase-0-results.md                                         (Task 15)
```

---

### Task 1: Dependencies — add the Anthropic SDK and upgrade google-genai

**Files:**
- Modify: `pom.xml` (dependencies block, near the existing `google-genai` entry at lines 44–48)

**Interfaces:**
- Consumes: nothing.
- Produces: `com.anthropic.*` and a `google-genai` version whose `com.google.genai.types.ImageConfig.Builder` has `aspectRatio(String)` and `imageSize(String)`.

- [ ] **Step 1: Find the newest google-genai release**

Run: `./mvnw -q versions:display-dependency-updates -Dincludes=com.google.genai:google-genai`
Expected: a line like `com.google.genai:google-genai ... 1.3.0 -> <newer version>`. Note the newer version; the steps below call it `<GENAI_VERSION>`.

- [ ] **Step 2: Edit `pom.xml`**

Replace the `google-genai` dependency version and add the Anthropic SDK right after it:

```xml
        <dependency>
            <groupId>com.google.genai</groupId>
            <artifactId>google-genai</artifactId>
            <version><GENAI_VERSION></version>
        </dependency>
        <dependency>
            <groupId>com.anthropic</groupId>
            <artifactId>anthropic-java</artifactId>
            <version>2.34.0</version>
        </dependency>
```

(Write the literal version number you noted in Step 1 in place of `<GENAI_VERSION>`.)

- [ ] **Step 3: Confirm the upgraded SDK can ask for 2K square images**

Run:
```bash
./mvnw -q dependency:resolve
jar=$(ls ~/.m2/repository/com/google/genai/google-genai/*/google-genai-*.jar | grep -v sources | sort -V | tail -1)
javap -cp "$jar" 'com.google.genai.types.ImageConfig$Builder' | grep -E "aspectRatio|imageSize"
javap -cp "$jar" 'com.google.genai.types.GenerateContentConfig$Builder' | grep -E "imageConfig"
javap -cp "$jar" com.google.genai.errors.ApiException | grep -E "code\(\)"
```
Expected: `aspectRatio(java.lang.String)`, `imageSize(java.lang.String)`, `imageConfig(...ImageConfig)` and `int code()` are all printed. If `imageSize` is missing, the version is still too old — pick a newer one and repeat.

- [ ] **Step 4: Check nothing existing broke**

`VertexImageClient`, `GeminiConfig` and the OCR Gemini service use the same SDK.

Run: `./mvnw -q -DskipTests compile && ./mvnw -q test -Dtest='ArabicPagePromptFactoryTest,RepetitionDetectorTest,HarmonizationGuardTest'`
Expected: BUILD SUCCESS. If compilation fails in `features/story/image/VertexImageClient.java` or `features/ocr/ai/impl/GeminiOcrServiceImpl.java`, fix only the renamed SDK calls the compiler points at; do not change behaviour.

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "build(storybook): add anthropic-java, upgrade google-genai for ImageConfig"
```

---

### Task 2: Enums and `StorybookProperties`

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/enums/AgeBand.java`, `ChildGender.java`, `LanguageVariety.java`, `TashkeelLevel.java`, `TextZone.java`, `LlmPurpose.java`, `Interest.java`, `StorySetting.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/config/StorybookProperties.java`
- Modify: `src/main/resources/application.properties` (append a storybook block)
- Test: `src/test/java/com/doova/ktab/features/storybook/config/StorybookPropertiesTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: the enums listed in the overview's "Shared contracts" (AI-facing set) and `StorybookProperties` with nested `Llm`, `Image`, `Pricing`, `LlmPrice`, `Limits`, `Worker`.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.config;

import com.doova.ktab.features.storybook.enums.AgeBand;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(StorybookProperties.class);

    @Test
    void defaultsMatchTheSpec() {
        runner.run(ctx -> {
            StorybookProperties p = ctx.getBean(StorybookProperties.class);
            assertThat(p.isEnabled()).isFalse();
            assertThat(p.getLlm().getModel()).isEqualTo("claude-sonnet-5");
            assertThat(p.getImage().getPrimaryModel()).isEqualTo("gemini-3.1-flash-image");
            assertThat(p.getImage().getFallbackModel()).isEqualTo("gemini-3-pro-image");
            assertThat(p.getImage().getImageSize()).isEqualTo("2K");
            assertThat(p.getImage().getAspectRatio()).isEqualTo("1:1");
            assertThat(p.getImage().getMaxGenerations()).isEqualTo(4);
            assertThat(p.getImage().getPrimaryGenerations()).isEqualTo(2);
            assertThat(p.getPricing().getImagePerImageUsd())
                    .containsEntry("gemini-3.1-flash-image", new BigDecimal("0.101"))
                    .containsEntry("gemini-3-pro-image", new BigDecimal("0.134"));
            assertThat(p.getPricing().getLlm().get("claude-sonnet-5").getOutputPerMillionUsd())
                    .isEqualByComparingTo("10.00");
            assertThat(p.getWorker().getConcurrency()).isEqualTo(4);
        });
    }

    @Test
    void bindsModelKeysThatContainDots() {
        runner.withPropertyValues(
                "ktab.storybook.enabled=true",
                "ktab.storybook.pricing.image-per-image-usd[gemini-3.1-flash-image]=0.050",
                "ktab.storybook.worker.poll-delay=5s"
        ).run(ctx -> {
            StorybookProperties p = ctx.getBean(StorybookProperties.class);
            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getPricing().getImagePerImageUsd().get("gemini-3.1-flash-image"))
                    .isEqualByComparingTo("0.050");
            assertThat(p.getWorker().getPollDelay()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void ageBandsCarryTheWordLimits() {
        assertThat(AgeBand.AGE_3_5.maxWordsPerPage()).isEqualTo(25);
        assertThat(AgeBand.AGE_6_8.maxWordsPerPage()).isEqualTo(45);
        assertThat(AgeBand.AGE_9_10.maxWordsPerPage()).isEqualTo(70);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StorybookPropertiesTest`
Expected: COMPILATION ERROR — `StorybookProperties` and `AgeBand` do not exist.

- [ ] **Step 3: Write the enums**

`AgeBand.java`:
```java
package com.doova.ktab.features.storybook.enums;

/** Word limits are decision D2 in the overview: starting values that Phase 0 tunes. */
public enum AgeBand {
    AGE_3_5(3, 5, 25),
    AGE_6_8(6, 8, 45),
    AGE_9_10(9, 10, 70);

    private final int minAge;
    private final int maxAge;
    private final int maxWordsPerPage;

    AgeBand(int minAge, int maxAge, int maxWordsPerPage) {
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.maxWordsPerPage = maxWordsPerPage;
    }

    public int minAge() { return minAge; }
    public int maxAge() { return maxAge; }
    public int maxWordsPerPage() { return maxWordsPerPage; }
}
```

`ChildGender.java`:
```java
package com.doova.ktab.features.storybook.enums;

public enum ChildGender {
    BOY("boy"), GIRL("girl");

    private final String en;
    ChildGender(String en) { this.en = en; }
    public String en() { return en; }
}
```

`LanguageVariety.java`:
```java
package com.doova.ktab.features.storybook.enums;

public enum LanguageVariety {
    MSA("Modern Standard Arabic", null),
    LEBANESE("Lebanese Arabic", "storybook/dialects/lebanese.md"),
    EGYPTIAN("Egyptian Arabic", "storybook/dialects/egyptian.md"),
    GULF("Gulf Arabic", "storybook/dialects/gulf.md");

    private final String en;
    private final String dialectGuideResource;

    LanguageVariety(String en, String dialectGuideResource) {
        this.en = en;
        this.dialectGuideResource = dialectGuideResource;
    }

    public String en() { return en; }
    public String dialectGuideResource() { return dialectGuideResource; }
    public boolean isDialect() { return this != MSA; }
}
```

`TashkeelLevel.java`:
```java
package com.doova.ktab.features.storybook.enums;

public enum TashkeelLevel { FULL, PARTIAL, NONE }
```

`TextZone.java`:
```java
package com.doova.ktab.features.storybook.enums;

/** Which third of the square illustration is left empty for the typeset text. */
public enum TextZone { TOP, BOTTOM }
```

`LlmPurpose.java`:
```java
package com.doova.ktab.features.storybook.enums;

public enum LlmPurpose { STORY_PLAN, STORY_PAGE_REWRITE, STORY_CRITIC, VISUAL_QA, MODERATION }
```

`Interest.java`:
```java
package com.doova.ktab.features.storybook.enums;

/** Structured on purpose: interests are never free text (spec, "Inputs"). */
public enum Interest {
    FOOTBALL("playing football"),
    CATS("cats"),
    DOGS("dogs"),
    DINOSAURS("dinosaurs"),
    SPACE("space and planets"),
    SEA_CREATURES("sea creatures"),
    DRAWING("drawing and colouring"),
    MUSIC("music and singing"),
    CARS("cars"),
    HORSES("horses"),
    BOOKS("reading books"),
    COOKING("helping in the kitchen");

    private final String en;
    Interest(String en) { this.en = en; }
    public String en() { return en; }
}
```

`StorySetting.java`:
```java
package com.doova.ktab.features.storybook.enums;

public enum StorySetting {
    BEIRUT("Beirut, Lebanon: sea promenade, old stone houses with red roofs, mountains behind"),
    CAIRO("Cairo, Egypt: the Nile, busy friendly streets, palm trees, the pyramids in the far distance"),
    RIYADH("Riyadh, Saudi Arabia: modern towers, desert-coloured houses, palm-lined streets"),
    DUBAI("Dubai, UAE: tall glass towers, the creek with wooden boats, sandy beaches"),
    AMMAN("Amman, Jordan: white stone houses on hills, stairs and narrow lanes"),
    GENERIC_CITY("a friendly Arab city with colourful houses and small shops"),
    COUNTRYSIDE("green countryside with olive trees, a small village and hills");

    private final String sceneEn;
    StorySetting(String sceneEn) { this.sceneEn = sceneEn; }
    public String sceneEn() { return sceneEn; }
}
```

- [ ] **Step 4: Write `StorybookProperties`**

```java
package com.doova.ktab.features.storybook.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the personalized storybook feature. Prices are the spec's
 * 2026-09-24 snapshot; re-check provider pricing before launch.
 * Map keys contain dots, so override them with bracket syntax:
 * {@code ktab.storybook.pricing.image-per-image-usd[gemini-3.1-flash-image]=0.101}.
 */
@Configuration
@ConfigurationProperties(prefix = "ktab.storybook")
@Getter
@Setter
public class StorybookProperties {

    /** Master switch for the worker and the public endpoints. */
    private boolean enabled = false;

    private Llm llm = new Llm();
    private Image image = new Image();
    private Pricing pricing = new Pricing();
    private Limits limits = new Limits();
    private Worker worker = new Worker();

    @Getter
    @Setter
    public static class Llm {
        private String model = "claude-sonnet-5";
        private Duration timeout = Duration.ofMinutes(5);
        private int maxRetries = 2;
    }

    @Getter
    @Setter
    public static class Image {
        private String primaryModel = "gemini-3.1-flash-image";
        private String fallbackModel = "gemini-3-pro-image";
        private int primaryMaxReferences = 4;
        private int fallbackMaxReferences = 5;
        private String aspectRatio = "1:1";
        private String imageSize = "2K";
        /** Generations 1..primaryGenerations use the primary model, later ones the fallback (D6). */
        private int primaryGenerations = 2;
        /** First generation plus 3 QA retries (spec: "retried up to 3 times"). */
        private int maxGenerations = 4;
        /** Longest side, in pixels, of images sent to Claude for visual QA. */
        private int qaMaxSidePx = 1024;
    }

    @Getter
    @Setter
    public static class LlmPrice {
        private BigDecimal inputPerMillionUsd;
        private BigDecimal outputPerMillionUsd;

        public LlmPrice() {
        }

        public LlmPrice(String input, String output) {
            this.inputPerMillionUsd = new BigDecimal(input);
            this.outputPerMillionUsd = new BigDecimal(output);
        }
    }

    @Getter
    @Setter
    public static class Pricing {
        private Map<String, LlmPrice> llm = new HashMap<>(Map.of(
                "claude-sonnet-5", new LlmPrice("2.00", "10.00")));
        private Map<String, BigDecimal> imagePerImageUsd = new HashMap<>(Map.of(
                "gemini-3.1-flash-image", new BigDecimal("0.101"),
                "gemini-3-pro-image", new BigDecimal("0.134")));
    }

    @Getter
    @Setter
    public static class Limits {
        private int dedicationMaxChars = 300;
        private int criticRewritesPerPage = 2;
        private int lookRegenerations = 2;
        private int pageRegenerationsPerBook = 3;
        private int draftsPerUserPerDay = 3;
        private BigDecimal maxBookCostUsd = new BigDecimal("6.00");
    }

    @Getter
    @Setter
    public static class Worker {
        private int concurrency = 4;
        private Duration pollDelay = Duration.ofSeconds(2);
        private Duration lease = Duration.ofMinutes(10);
        private Duration baseBackoff = Duration.ofSeconds(10);
        private int maxAttempts = 5;
    }
}
```

- [ ] **Step 5: Append the defaults to `application.properties`**

Append at the end of `src/main/resources/application.properties`:

```properties
# ===== Personalized storybook (docs/superpowers/plans/2026-09-24-storybook-00-overview.md) =====
ktab.storybook.enabled=${KTAB_STORYBOOK_ENABLED:false}
ktab.storybook.llm.model=${KTAB_STORYBOOK_LLM_MODEL:claude-sonnet-5}
ktab.storybook.image.primary-model=${KTAB_STORYBOOK_IMAGE_PRIMARY:gemini-3.1-flash-image}
ktab.storybook.image.fallback-model=${KTAB_STORYBOOK_IMAGE_FALLBACK:gemini-3-pro-image}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=StorybookPropertiesTest`
Expected: 3 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/enums src/main/java/com/doova/ktab/features/storybook/config/StorybookProperties.java src/main/resources/application.properties src/test/java/com/doova/ktab/features/storybook/config
git commit -m "feat(storybook): add storybook enums and configuration properties"
```

---

### Task 3: `CostCalculator`

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/cost/CostCalculator.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/cost/CostCalculatorTest.java`

**Interfaces:**
- Consumes: `StorybookProperties.getPricing()` (Task 2).
- Produces: `BigDecimal llmCostUsd(String model, long inputTokens, long outputTokens)`, `BigDecimal imageCostUsd(String model)`. Both scale 6. Unknown model → `IllegalStateException` (a missing price is a config bug; do not silently log $0).

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.cost;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostCalculatorTest {

    private final CostCalculator calculator = new CostCalculator(new StorybookProperties());

    @Test
    void llmCostUsesPerMillionPrices() {
        // 1,000 in at $2/M + 500 out at $10/M = 0.002 + 0.005
        assertThat(calculator.llmCostUsd("claude-sonnet-5", 1_000, 500)).isEqualByComparingTo("0.007");
    }

    @Test
    void imageCostIsPerImage() {
        assertThat(calculator.imageCostUsd("gemini-3.1-flash-image")).isEqualByComparingTo("0.101");
    }

    @Test
    void unknownModelIsAConfigurationError() {
        assertThatThrownBy(() -> calculator.imageCostUsd("some-new-model"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("some-new-model");
        assertThatThrownBy(() -> calculator.llmCostUsd("claude-unknown", 1, 1))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=CostCalculatorTest`
Expected: COMPILATION ERROR — `CostCalculator` does not exist.

- [ ] **Step 3: Implement**

```java
package com.doova.ktab.features.storybook.cost;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class CostCalculator {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final StorybookProperties properties;

    public BigDecimal llmCostUsd(String model, long inputTokens, long outputTokens) {
        StorybookProperties.LlmPrice price = properties.getPricing().getLlm().get(model);
        if (price == null) {
            throw new IllegalStateException("No LLM price configured for model " + model);
        }
        return price.getInputPerMillionUsd().multiply(BigDecimal.valueOf(inputTokens))
                .add(price.getOutputPerMillionUsd().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(ONE_MILLION, 6, RoundingMode.HALF_UP);
    }

    public BigDecimal imageCostUsd(String model) {
        BigDecimal price = properties.getPricing().getImagePerImageUsd().get(model);
        if (price == null) {
            throw new IllegalStateException("No image price configured for model " + model);
        }
        return price.setScale(6, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=CostCalculatorTest`
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/cost src/test/java/com/doova/ktab/features/storybook/cost
git commit -m "feat(storybook): add cost calculator for LLM and image calls"
```

---

### Task 4: Claude gateway (`LlmGateway` + `AnthropicLlmGateway`)

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/llm/LlmGateway.java`, `LlmRequest.java`, `LlmImage.java`, `LlmCall.java`, `LlmCallFailedException.java`, `AnthropicLlmGateway.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/config/AnthropicClientConfig.java`
- Modify: `src/main/resources/application.properties` (API key line)
- Test: `src/test/java/com/doova/ktab/features/storybook/llm/LlmRequestTest.java`, `src/test/java/com/doova/ktab/features/storybook/llm/AnthropicLlmGatewayLiveTest.java`

**Interfaces:**
- Consumes: `StorybookProperties.getLlm()` (Task 2), `LlmPurpose` (Task 2).
- Produces:
  - `interface LlmGateway { <T> LlmCall<T> call(LlmRequest<T> request); }`
  - `record LlmRequest<T>(LlmPurpose purpose, String system, String user, List<LlmImage> images, Class<T> responseType, int maxTokens)` with factory `static <T> LlmRequest<T> of(LlmPurpose, String system, String user, Class<T> type)` (maxTokens 16000) and `withImages(List<LlmImage>)`.
  - `record LlmImage(byte[] bytes, String mediaType)` — mediaType `image/png` or `image/jpeg`.
  - `record LlmCall<T>(T value, String model, long inputTokens, long outputTokens, long latencyMs)`.
  - `LlmCallFailedException(String message, boolean retryable, Throwable cause)` with `boolean retryable()`.

Design notes for the implementer:
- Claude is called with **structured outputs** via the SDK's class-based overload `MessageCreateParams.builder()...outputConfig(Class<T>)`, which derives the JSON schema from the record and returns a typed `StructuredMessageCreateParams<T>`. Records may use `@JsonPropertyDescription`.
- `thinking` is not set: on `claude-sonnet-5`, omitting it runs adaptive thinking. Do not send `budget_tokens` (400 on Sonnet 5) and do not prefill assistant turns (400).
- A `refusal` stop reason is non-retryable; `max_tokens` means the structured JSON is truncated — also non-retryable with the same request.
- Retry classification uses `com.anthropic.errors`: `RateLimitException`, `InternalServerException`, `AnthropicIoException` are retryable; everything else is not. The SDK already retries transient errors `maxRetries` times before we see them.

- [ ] **Step 1: Write the failing unit test**

```java
package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.enums.LlmPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRequestTest {

    record Answer(String text) {}

    @Test
    void factoryDefaultsToNoImagesAndSixteenThousandTokens() {
        LlmRequest<Answer> r = LlmRequest.of(LlmPurpose.MODERATION, "sys", "user", Answer.class);
        assertThat(r.images()).isEmpty();
        assertThat(r.maxTokens()).isEqualTo(16000);
    }

    @Test
    void imagesAreDefensivelyCopied() {
        List<LlmImage> images = new ArrayList<>(List.of(new LlmImage(new byte[]{1}, "image/png")));
        LlmRequest<Answer> r = LlmRequest.of(LlmPurpose.VISUAL_QA, "sys", "user", Answer.class).withImages(images);
        images.clear();
        assertThat(r.images()).hasSize(1);
    }

    @Test
    void rejectsUnsupportedImageTypes() {
        assertThatThrownBy(() -> new LlmImage(new byte[]{1}, "image/webp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresSystemAndUserText() {
        assertThatThrownBy(() -> LlmRequest.of(LlmPurpose.MODERATION, " ", "user", Answer.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=LlmRequestTest`
Expected: COMPILATION ERROR — `LlmRequest` does not exist.

- [ ] **Step 3: Write the gateway types**

`LlmGateway.java`:
```java
package com.doova.ktab.features.storybook.llm;

/** The only way storybook code talks to an LLM. Tests use {@code FakeLlmGateway}. */
public interface LlmGateway {
    <T> LlmCall<T> call(LlmRequest<T> request);
}
```

`LlmImage.java`:
```java
package com.doova.ktab.features.storybook.llm;

import java.util.Objects;
import java.util.Set;

public record LlmImage(byte[] bytes, String mediaType) {

    private static final Set<String> SUPPORTED = Set.of("image/png", "image/jpeg");

    public LlmImage {
        Objects.requireNonNull(bytes, "bytes");
        if (!SUPPORTED.contains(mediaType)) {
            throw new IllegalArgumentException("Unsupported image media type: " + mediaType);
        }
    }
}
```

`LlmRequest.java`:
```java
package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.enums.LlmPurpose;

import java.util.List;
import java.util.Objects;

public record LlmRequest<T>(
        LlmPurpose purpose,
        String system,
        String user,
        List<LlmImage> images,
        Class<T> responseType,
        int maxTokens
) {
    public LlmRequest {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(responseType, "responseType");
        if (system == null || system.isBlank() || user == null || user.isBlank()) {
            throw new IllegalArgumentException("system and user text are required");
        }
        images = images == null ? List.of() : List.copyOf(images);
    }

    public static <T> LlmRequest<T> of(LlmPurpose purpose, String system, String user, Class<T> responseType) {
        return new LlmRequest<>(purpose, system, user, List.of(), responseType, 16000);
    }

    public LlmRequest<T> withImages(List<LlmImage> newImages) {
        return new LlmRequest<>(purpose, system, user, newImages, responseType, maxTokens);
    }
}
```

`LlmCall.java`:
```java
package com.doova.ktab.features.storybook.llm;

public record LlmCall<T>(T value, String model, long inputTokens, long outputTokens, long latencyMs) {
}
```

`LlmCallFailedException.java`:
```java
package com.doova.ktab.features.storybook.llm;

public class LlmCallFailedException extends RuntimeException {

    private final boolean retryable;

    public LlmCallFailedException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
```

- [ ] **Step 4: Run the unit test to verify it passes**

Run: `./mvnw -q test -Dtest=LlmRequestTest`
Expected: 4 tests PASS.

- [ ] **Step 5: Write the client bean**

Append to `src/main/resources/application.properties`:
```properties
ktab.storybook.llm.api-key=${ANTHROPIC_API_KEY:}
```

Add the field to `StorybookProperties.Llm`:
```java
        /** Read from ANTHROPIC_API_KEY. Never commit a key. */
        private String apiKey;
```

`AnthropicClientConfig.java`:
```java
package com.doova.ktab.features.storybook.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnthropicClientConfig {

    @Bean(destroyMethod = "close")
    public AnthropicClient storybookAnthropicClient(StorybookProperties properties) {
        StorybookProperties.Llm llm = properties.getLlm();
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .timeout(llm.getTimeout())
                .maxRetries(llm.getMaxRetries());
        if (llm.getApiKey() != null && !llm.getApiKey().isBlank()) {
            builder.apiKey(llm.getApiKey());
        } else {
            // Lets local development start without a key; calls fail with an auth error.
            builder.apiKey("missing-anthropic-api-key");
        }
        return builder.build();
    }
}
```

If the compiler reports that `AnthropicClient` has no `close()` method, remove `destroyMethod = "close"`.

- [ ] **Step 6: Write `AnthropicLlmGateway`**

```java
package com.doova.ktab.features.storybook.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnthropicLlmGateway implements LlmGateway {

    private final AnthropicClient storybookAnthropicClient;
    private final StorybookProperties properties;

    @Override
    public <T> LlmCall<T> call(LlmRequest<T> request) {
        String model = properties.getLlm().getModel();

        List<ContentBlockParam> blocks = new ArrayList<>();
        for (LlmImage image : request.images()) {
            blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .data(Base64.getEncoder().encodeToString(image.bytes()))
                            .mediaType("image/png".equals(image.mediaType())
                                    ? Base64ImageSource.MediaType.IMAGE_PNG
                                    : Base64ImageSource.MediaType.IMAGE_JPEG)
                            .build())
                    .build()));
        }
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(request.user()).build()));

        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens())
                .system(request.system())
                .addUserMessageOfBlockParams(blocks)
                .outputConfig(request.responseType())
                .build();

        long started = System.nanoTime();
        StructuredMessage<T> message;
        try {
            message = storybookAnthropicClient.messages().create(params);
        } catch (RateLimitException | InternalServerException | AnthropicIoException e) {
            throw new LlmCallFailedException(request.purpose() + " call failed transiently", true, e);
        } catch (RuntimeException e) {
            throw new LlmCallFailedException(request.purpose() + " call failed", false, e);
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000;

        StopReason stop = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new LlmCallFailedException(request.purpose() + " was refused by the model", false, null);
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new LlmCallFailedException(request.purpose() + " hit max_tokens; output truncated", false, null);
        }

        T value = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new LlmCallFailedException(
                        request.purpose() + " returned no structured output", true, null));

        log.debug("storybook llm purpose={} model={} in={} out={} latencyMs={}", request.purpose(), model,
                message.usage().inputTokens(), message.usage().outputTokens(), latencyMs);

        return new LlmCall<>(value, model, message.usage().inputTokens(), message.usage().outputTokens(), latencyMs);
    }
}
```

Compile: `./mvnw -q -DskipTests compile`. The SDK names above follow the skill's Java reference (`StructuredMessageCreateParams`, `outputConfig(Class)`, `ContentBlockParam.ofImage`, `Base64ImageSource`). If the compiler rejects a name, fix it from the compiler message or `javap -cp <anthropic-java-core jar> com.anthropic.models.messages.<Class>`; do not change the behaviour described in the design notes.

- [ ] **Step 7: Write the live smoke test (skipped unless explicitly enabled)**

```java
package com.doova.ktab.features.storybook.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "STORYBOOK_LIVE_TESTS", matches = "true")
class AnthropicLlmGatewayLiveTest {

    record Capital(@JsonPropertyDescription("The capital city, in English") String city) {}

    @Test
    void returnsTypedStructuredOutputAndUsage() {
        AnthropicLlmGateway gateway = new AnthropicLlmGateway(AnthropicOkHttpClient.fromEnv(), new StorybookProperties());

        LlmCall<Capital> call = gateway.call(LlmRequest.of(LlmPurpose.MODERATION,
                "Answer the question.", "What is the capital of Lebanon?", Capital.class));

        assertThat(call.value().city()).containsIgnoringCase("beirut");
        assertThat(call.model()).isEqualTo("claude-sonnet-5");
        assertThat(call.inputTokens()).isPositive();
        assertThat(call.outputTokens()).isPositive();
    }
}
```

Run: `STORYBOOK_LIVE_TESTS=true ./mvnw -q test -Dtest=AnthropicLlmGatewayLiveTest`
Expected: PASS (needs `ANTHROPIC_API_KEY`). Without the variable the test is skipped.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/llm src/main/java/com/doova/ktab/features/storybook/config src/main/resources/application.properties src/test/java/com/doova/ktab/features/storybook/llm
git commit -m "feat(storybook): add Claude gateway with structured outputs and image input"
```

---

### Task 5: Image provider (`ImageProvider` + `GeminiImageProvider`)

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/image/ImageProvider.java`, `ImageRequest.java`, `ReferenceImage.java`, `ImageResult.java`, `ImageGenerationException.java`, `GeminiImageProvider.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/image/GeminiImageProviderTest.java`, `GeminiImageProviderLiveTest.java`

**Interfaces:**
- Consumes: the `com.google.genai.Client vertexGenAiClient` bean from `features/ai/config/GeminiConfig.java`; `StorybookProperties.getImage()`.
- Produces:
  - `interface ImageProvider { ImageResult generate(ImageRequest request); }`
  - `record ImageRequest(String model, String prompt, List<ReferenceImage> references)` — references in the order they should be shown to the model.
  - `record ReferenceImage(byte[] bytes, String mimeType)`
  - `record ImageResult(byte[] bytes, String mimeType, String model, long latencyMs)`
  - `ImageGenerationException(String message, boolean retryable, Throwable cause)` with `boolean retryable()`.
  - Package-private statics used by the test: `GenerateContentConfig buildConfig(StorybookProperties.Image cfg)` and `Content buildContent(ImageRequest request)`.

- [ ] **Step 1: Write the failing unit test**

```java
package com.doova.ktab.features.storybook.image;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiImageProviderTest {

    @Test
    void configAsksForOneSquare2kImage() {
        GenerateContentConfig config = GeminiImageProvider.buildConfig(new StorybookProperties.Image());

        assertThat(config.responseModalities().orElseThrow().toString()).contains("IMAGE");
        assertThat(config.imageConfig().orElseThrow().aspectRatio()).contains("1:1");
        assertThat(config.imageConfig().orElseThrow().imageSize()).contains("2K");
    }

    @Test
    void safetyFiltersAreNeverOff() {
        GenerateContentConfig config = GeminiImageProvider.buildConfig(new StorybookProperties.Image());

        List<SafetySetting> settings = config.safetySettings().orElseThrow();
        assertThat(settings).hasSize(4);
        assertThat(settings).allSatisfy(s ->
                assertThat(s.threshold().orElseThrow().toString()).contains("BLOCK_LOW_AND_ABOVE"));
    }

    @Test
    void referenceImagesComeBeforeThePrompt() {
        ImageRequest request = new ImageRequest("gemini-3.1-flash-image", "draw a cat",
                List.of(new ReferenceImage(new byte[]{1, 2}, "image/png"), new ReferenceImage(new byte[]{3}, "image/png")));

        Content content = GeminiImageProvider.buildContent(request);

        List<Part> parts = content.parts().orElseThrow();
        assertThat(parts).hasSize(3);
        assertThat(parts.get(0).inlineData()).isPresent();
        assertThat(parts.get(1).inlineData()).isPresent();
        assertThat(parts.get(2).text()).contains("draw a cat");
    }

    @Test
    void rejectsMoreThanFiveReferences() {
        List<ReferenceImage> six = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new ReferenceImage(new byte[]{1}, "image/png")).toList();
        assertThatThrownBy(() -> new ImageRequest("m", "p", six)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=GeminiImageProviderTest`
Expected: COMPILATION ERROR — `GeminiImageProvider` does not exist.

- [ ] **Step 3: Write the types**

`ImageProvider.java`:
```java
package com.doova.ktab.features.storybook.image;

public interface ImageProvider {
    ImageResult generate(ImageRequest request);
}
```

`ReferenceImage.java`:
```java
package com.doova.ktab.features.storybook.image;

import java.util.Objects;

public record ReferenceImage(byte[] bytes, String mimeType) {
    public ReferenceImage {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mimeType, "mimeType");
    }
}
```

`ImageRequest.java`:
```java
package com.doova.ktab.features.storybook.image;

import java.util.List;
import java.util.Objects;

public record ImageRequest(String model, String prompt, List<ReferenceImage> references) {

    /** Nano Banana Pro accepts up to 5 references; Nano Banana 2 up to 4 (checked by the caller). */
    public static final int MAX_REFERENCES = 5;

    public ImageRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(prompt, "prompt");
        references = references == null ? List.of() : List.copyOf(references);
        if (references.size() > MAX_REFERENCES) {
            throw new IllegalArgumentException("At most " + MAX_REFERENCES + " reference images");
        }
    }
}
```

`ImageResult.java`:
```java
package com.doova.ktab.features.storybook.image;

public record ImageResult(byte[] bytes, String mimeType, String model, long latencyMs) {
}
```

`ImageGenerationException.java`:
```java
package com.doova.ktab.features.storybook.image;

public class ImageGenerationException extends RuntimeException {

    private final boolean retryable;

    public ImageGenerationException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
```

- [ ] **Step 4: Write `GeminiImageProvider`**

```java
package com.doova.ktab.features.storybook.image;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Nano Banana (Gemini image) on Vertex AI through Ktab's existing {@code vertexGenAiClient}.
 * Deliberately NOT built on features.story.image.VertexImageClient: that class turns every
 * safety filter OFF, which is unacceptable for children's books.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiImageProvider implements ImageProvider {

    private static final List<String> HARM_CATEGORIES = List.of(
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_DANGEROUS_CONTENT",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_HARASSMENT");

    private final Client vertexGenAiClient;
    private final StorybookProperties properties;

    @Override
    public ImageResult generate(ImageRequest request) {
        long started = System.nanoTime();
        GenerateContentResponse response;
        try {
            response = vertexGenAiClient.models.generateContent(
                    request.model(), List.of(buildContent(request)), buildConfig(properties.getImage()));
        } catch (ApiException e) {
            boolean retryable = e.code() == 429 || e.code() >= 500;
            throw new ImageGenerationException("Image generation failed with HTTP " + e.code(), retryable, e);
        } catch (RuntimeException e) {
            throw new ImageGenerationException("Image generation failed", true, e);
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000;

        List<Part> parts = response.parts();
        if (parts != null) {
            for (Part part : parts) {
                if (part.inlineData().isPresent() && part.inlineData().get().data().isPresent()) {
                    String mime = part.inlineData().get().mimeType().orElse("image/png");
                    return new ImageResult(part.inlineData().get().data().get(), mime, request.model(), latencyMs);
                }
            }
        }
        // Usually a safety block. Retrying is worthwhile: a harmless children's scene is often
        // accepted on a second attempt, and the retry count is bounded by the caller.
        String finish = response.finishReason() == null ? "unknown" : response.finishReason().toString();
        throw new ImageGenerationException("No image returned (finishReason=" + finish + ")", true, null);
    }

    static GenerateContentConfig buildConfig(StorybookProperties.Image cfg) {
        List<SafetySetting> safety = new ArrayList<>();
        for (String category : HARM_CATEGORIES) {
            safety.add(SafetySetting.builder().category(category).threshold("BLOCK_LOW_AND_ABOVE").build());
        }
        return GenerateContentConfig.builder()
                .responseModalities(List.of("IMAGE"))
                .imageConfig(ImageConfig.builder()
                        .aspectRatio(cfg.getAspectRatio())
                        .imageSize(cfg.getImageSize())
                        .build())
                .candidateCount(1)
                .safetySettings(safety)
                .build();
    }

    static Content buildContent(ImageRequest request) {
        List<Part> parts = new ArrayList<>();
        for (ReferenceImage reference : request.references()) {
            parts.add(Part.fromBytes(reference.bytes(), reference.mimeType()));
        }
        parts.add(Part.fromText(request.prompt()));
        return Content.builder().role("user").parts(parts).build();
    }
}
```

If the compiler reports that `response.finishReason()` does not exist, replace that line with `String finish = "unknown";` — it is only used in the error message.

- [ ] **Step 5: Run the unit test to verify it passes**

Run: `./mvnw -q test -Dtest=GeminiImageProviderTest`
Expected: 4 tests PASS. If `threshold().orElseThrow().toString()` prints a wrapper name instead of the value, change that assertion to compare `s.threshold().orElseThrow().knownEnum().toString()` (or whatever accessor `javap` shows), keeping the same intent.

- [ ] **Step 6: Write the live smoke test**

```java
package com.doova.ktab.features.storybook.image;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "STORYBOOK_LIVE_TESTS", matches = "true")
class GeminiImageProviderLiveTest {

    @Test
    void generatesASquareImage() throws Exception {
        Client client = Client.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location("global")
                .vertexAI(true)
                .credentials(GoogleCredentials.getApplicationDefault())
                .build();
        GeminiImageProvider provider = new GeminiImageProvider(client, new StorybookProperties());

        ImageResult result = provider.generate(new ImageRequest("gemini-3.1-flash-image",
                "A friendly orange cat sitting in a sunny garden, children's book watercolour. No text.", List.of()));

        assertThat(result.bytes().length).isGreaterThan(10_000);
        assertThat(result.mimeType()).startsWith("image/");
    }
}
```

Run: `STORYBOOK_LIVE_TESTS=true GCP_PROJECT_ID=<your project> ./mvnw -q test -Dtest=GeminiImageProviderLiveTest`
Expected: PASS. A 404/400 naming the model means Nano Banana 2 is not enabled for the project or region — resolve that before the spike (it is also the spec's "confirm SLA before launch" item for a preview model).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/image src/test/java/com/doova/ktab/features/storybook/image
git commit -m "feat(storybook): add Gemini image provider with child-safe filters and 2K square output"
```

---

### Task 6: Prompt library, prompts and dialect guides

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/prompt/PromptLibrary.java`
- Create: `src/main/resources/storybook/prompts/story-plan-system.md`, `page-rewrite-system.md`, `critic-system.md`, `moderation-system.md`, `visual-qa-system.md`
- Create: `src/main/resources/storybook/dialects/lebanese.md`, `egyptian.md`, `gulf.md`
- Test: `src/test/java/com/doova/ktab/features/storybook/prompt/PromptLibraryTest.java`

**Interfaces:**
- Consumes: `LanguageVariety.dialectGuideResource()` (Task 2).
- Produces: `PromptLibrary.get(String name)` → contents of `storybook/prompts/<name>.md`; `PromptLibrary.dialectGuide(LanguageVariety v)` → guide text, or `""` for MSA. Both cache and throw `IllegalStateException` on a missing file.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.prompt;

import com.doova.ktab.features.storybook.enums.LanguageVariety;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLibraryTest {

    private final PromptLibrary library = new PromptLibrary();

    @ParameterizedTest
    @ValueSource(strings = {"story-plan-system", "page-rewrite-system", "critic-system",
            "moderation-system", "visual-qa-system"})
    void everyPromptExistsAndIsNotEmpty(String name) {
        assertThat(library.get(name)).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(value = LanguageVariety.class, names = {"LEBANESE", "EGYPTIAN", "GULF"})
    void everyDialectHasAGuideWithItsNoTashkeelRule(LanguageVariety variety) {
        assertThat(library.dialectGuide(variety)).contains("No tashkeel");
    }

    @Test
    void msaHasNoDialectGuide() {
        assertThat(library.dialectGuide(LanguageVariety.MSA)).isEmpty();
    }

    @Test
    void missingPromptIsAnError() {
        assertThatThrownBy(() -> library.get("does-not-exist")).isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=PromptLibraryTest`
Expected: COMPILATION ERROR — `PromptLibrary` does not exist.

- [ ] **Step 3: Implement `PromptLibrary`**

```java
package com.doova.ktab.features.storybook.prompt;

import com.doova.ktab.features.storybook.enums.LanguageVariety;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptLibrary {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String name) {
        return load("storybook/prompts/" + name + ".md");
    }

    public String dialectGuide(LanguageVariety variety) {
        return variety.isDialect() ? load(variety.dialectGuideResource()) : "";
    }

    private String load(String path) {
        return cache.computeIfAbsent(path, p -> {
            ClassPathResource resource = new ClassPathResource(p);
            if (!resource.exists()) {
                throw new IllegalStateException("Missing storybook resource: " + p);
            }
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read storybook resource: " + p, e);
            }
        });
    }
}
```

- [ ] **Step 4: Write the dialect guides**

Copy each dialect style guide **verbatim** from the spec (`docs/superpowers/specs/2026-09-24-personalized-storybook-spec.md`), from its `# … dialect style guide` heading down to the end of its "Example" table, into:
- `src/main/resources/storybook/dialects/lebanese.md` — the "Lebanese dialect style guide" section
- `src/main/resources/storybook/dialects/egyptian.md` — the "Egyptian dialect style guide" section
- `src/main/resources/storybook/dialects/gulf.md` — the "Gulf dialect style guide" section

Each file starts with its `# … dialect style guide` line and contains rule 1 "No tashkeel." (the test checks for it). Save as UTF-8.

- [ ] **Step 5: Write the prompts**

These are the system prompts. They are static (no per-book values) so Anthropic prompt caching can reuse them; per-book details go in the user message (Task 8).

`src/main/resources/storybook/prompts/story-plan-system.md`:
```markdown
You write personalized Arabic picture books for young children. You receive a plot blueprint with one beat per page, the child's details, and the language variety to write in. You write each page's Arabic text and describe its illustration in English.

Arabic text
- Follow the language-variety instructions in the request exactly. For Modern Standard Arabic, write fully vocalized text: tashkeel on every letter that needs it, including case endings. For a dialect, use no tashkeel at all and follow the attached style guide.
- Each page has 1 to 4 short sentences and stays within the word limit given in the request. Vocabulary and sentence length suit the child's age.
- Write the child's name exactly as given, character for character, every time it appears.
- Every verb, adjective and pronoun that refers to the child agrees with the child's gender.
- Follow each page's blueprint beat. Add warmth, sensory detail and the child's interests where they fit, but no new plot turns.
- Suitable for young children: no violence, nothing frightening beyond gentle suspense, no romance, no brand names, nothing a parent would hesitate to read aloud. People wear modest clothing. Settings are culturally appropriate.
- The title is 2 to 5 words, in the same language variety as the pages.

Illustration descriptions (English)
- Describe what the picture shows: place, action, poses, facial expressions, time of day. 2 to 4 sentences.
- Refer to the characters only as CHILD and COMPANION.
- Never ask for text, letters, numbers, signs, labels, book covers with writing, or screens with writing.
- Choose a text zone, TOP or BOTTOM, and describe that third of the picture as calm and empty (sky, wall, grass, water, floor) so text can be printed over it.
- The cover shows the CHILD (and COMPANION if there is one) in the story's main setting, with a calm TOP third for the title.
```

`src/main/resources/storybook/prompts/page-rewrite-system.md`:
```markdown
You are revising one page of a personalized Arabic picture book. An editor found problems with it. Rewrite the page so every listed problem is fixed while keeping the same blueprint beat, the same scene, and the same language-variety rules as the rest of the book (given in the request). Keep the child's name exactly as given. Keep 1 to 4 sentences and stay within the word limit. Return the full revised page, including an English scene description and text zone.
```

`src/main/resources/storybook/prompts/critic-system.md`:
```markdown
You are the Arabic editor of a children's picture-book publisher. You check a generated book before a parent sees it. For every page (page 0 is the title) decide pass or fail, and list each problem in one short English sentence that quotes the offending Arabic words.

Fail a page if any of these is true:
- A verb, adjective or pronoun referring to the child does not agree with the child's gender given in the request.
- Modern Standard Arabic: a tashkeel mark is wrong, or a word that needs vocalization for a young reader is missing it. Dialect: the page breaks a rule in the attached style guide, or contains any tashkeel.
- The grammar is wrong, a sentence is unclear, or the vocabulary is too hard for the child's age.
- Anything is inappropriate for a young child: violence, fear beyond gentle suspense, romance, insults, slang, brand names, or anything a parent would hesitate to read aloud.

Do not fail a page for style preferences. Do not check word counts or the spelling of the child's name; those are checked separately.
```

`src/main/resources/storybook/prompts/moderation-system.md`:
```markdown
You review a short dedication that a parent wants printed on the first page of their child's picture book. Decide whether it may be printed.

Allow warm, ordinary family messages in any language, including prayers and blessings.

Refuse it if it contains any of: profanity or insults; sexual content; violence or threats; hate towards any group; political slogans; advertising; links, email addresses or phone numbers; surnames together with a school, street or other location that could identify the child.

If you refuse, give the reason in one short English sentence the parent can act on.
```

`src/main/resources/storybook/prompts/visual-qa-system.md`:
```markdown
You check one illustration for a children's picture book before it is printed. You receive reference images first, then the illustration to check last. The first reference is the character sheet of the CHILD; a later reference may be the COMPANION's sheet; one reference shows the art style.

Report:
- identityMatch: the CHILD in the illustration is clearly the same character as in the sheet — face shape, skin tone, hair colour and style, eye colour, glasses, hijab, and clothes. Small pose and expression changes are fine; a different hair colour, skin tone or missing hijab is not. If the scene says the CHILD is present but they are missing, this is false.
- strayText: any letters, words, numbers, logos or writing-like marks appear anywhere in the illustration.
- anatomyOk: no extra or missing fingers, limbs or eyes, no merged or distorted faces or bodies.
- safeForChildren: nothing frightening, violent, immodest or otherwise unsuitable for a young child.
- problems: one short sentence per issue you found; empty if none.
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=PromptLibraryTest`
Expected: 9 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/prompt src/main/resources/storybook src/test/java/com/doova/ktab/features/storybook/prompt
git commit -m "feat(storybook): add prompt library, system prompts and dialect style guides"
```

---

### Task 7: Blueprints (`Blueprint`, `BlueprintBeat`, `BlueprintCatalog`)

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/blueprint/Blueprint.java`, `BlueprintBeat.java`, `BlueprintCatalog.java`
- Create: `src/main/resources/storybook/blueprints/first-day-of-school.v1.json`
- Test: `src/test/java/com/doova/ktab/features/storybook/blueprint/BlueprintCatalogTest.java`, `src/test/resources/storybook/blueprints-invalid/broken.v1.json`

**Interfaces:**
- Consumes: `AgeBand`, `StorySetting` (Task 2), Jackson `ObjectMapper`.
- Produces:
  - `record BlueprintBeat(int order, int minPageCount, String beat)` — the beat is included in books with `pageCount >= minPageCount`.
  - `record Blueprint(String key, int version, String titleAr, String titleEn, String theme, List<AgeBand> ageBands, List<StorySetting> allowedSettings, boolean religious, List<BlueprintBeat> beats)` with `List<BlueprintBeat> beatsFor(int pageCount)`.
  - `BlueprintCatalog(ObjectMapper mapper, String locationPattern)`; Spring constructor uses `classpath*:storybook/blueprints/*.json`. Methods: `Blueprint get(String key)` (latest version; `IllegalArgumentException` if unknown), `List<Blueprint> forAgeBand(AgeBand band)`, `List<Blueprint> all()`.
  - Validation at load (fails startup): file name is `<key>.v<version>.json`; beat `order` values are exactly 1..15; `beatsFor(10)`, `beatsFor(12)`, `beatsFor(15)` return exactly 10, 12 and 15 beats; `minPageCount` is one of 10, 12, 15; at least one age band.

Blueprint JSON schema (editors write these; see overview prerequisites):
```json
{
  "key": "kebab-case-key",
  "version": 1,
  "titleAr": "…",
  "titleEn": "…",
  "theme": "school | adventure | space | sea | eid | ramadan | new-sibling | honesty | …",
  "ageBands": ["AGE_3_5", "AGE_6_8"],
  "allowedSettings": ["BEIRUT", "CAIRO", "GENERIC_CITY"],
  "religious": false,
  "beats": [ { "order": 1, "minPageCount": 10, "beat": "English description of what happens on this page" } ]
}
```

- [ ] **Step 1: Write the sample blueprint**

`src/main/resources/storybook/blueprints/first-day-of-school.v1.json` — exactly 10 beats with `minPageCount` 10, 2 with 12, and 3 with 15:
```json
{
  "key": "first-day-of-school",
  "version": 1,
  "titleAr": "يومي الأول في المدرسة",
  "titleEn": "My First Day at School",
  "theme": "school",
  "ageBands": ["AGE_3_5", "AGE_6_8"],
  "allowedSettings": ["BEIRUT", "CAIRO", "RIYADH", "DUBAI", "AMMAN", "GENERIC_CITY", "COUNTRYSIDE"],
  "religious": false,
  "beats": [
    {"order": 1,  "minPageCount": 10, "beat": "The CHILD wakes up early, excited and a little nervous: today is the first day of school."},
    {"order": 2,  "minPageCount": 10, "beat": "The CHILD gets ready: new clothes, a new school bag, breakfast with the family."},
    {"order": 3,  "minPageCount": 15, "beat": "The CHILD checks the school bag twice: pencils, a lunch box, and a favourite small toy for courage."},
    {"order": 4,  "minPageCount": 10, "beat": "On the way to school the CHILD holds a parent's hand and notices the busy, friendly streets of the setting."},
    {"order": 5,  "minPageCount": 10, "beat": "At the school gate the CHILD hesitates; the building looks very big."},
    {"order": 6,  "minPageCount": 12, "beat": "The parent kneels down, hugs the CHILD and says something encouraging about being brave."},
    {"order": 7,  "minPageCount": 10, "beat": "The kind teacher welcomes the CHILD with a warm smile and shows the classroom."},
    {"order": 8,  "minPageCount": 15, "beat": "The classroom is full of colour: drawings on the walls, a reading corner, a plant by the window."},
    {"order": 9,  "minPageCount": 10, "beat": "The CHILD sits next to another child who is also shy; they share a small smile."},
    {"order": 10, "minPageCount": 12, "beat": "During the lesson the CHILD answers a question and the teacher praises the answer."},
    {"order": 11, "minPageCount": 10, "beat": "At break time the CHILD and the new friend play together, connected to the CHILD's interests."},
    {"order": 12, "minPageCount": 15, "beat": "The two friends share their snacks and laugh about something funny that happened."},
    {"order": 13, "minPageCount": 10, "beat": "The CHILD draws a picture of the new friend and the school during the art activity."},
    {"order": 14, "minPageCount": 10, "beat": "When the parent comes to pick them up, the CHILD runs over proudly to show the drawing."},
    {"order": 15, "minPageCount": 10, "beat": "At home the CHILD happily says they cannot wait to go back to school tomorrow."}
  ]
}
```

- [ ] **Step 2: Write the invalid fixture**

`src/test/resources/storybook/blueprints-invalid/broken.v1.json` (only 11 beats with minPageCount 10 — wrong):
```json
{
  "key": "broken", "version": 1, "titleAr": "خطأ", "titleEn": "Broken", "theme": "test",
  "ageBands": ["AGE_6_8"], "allowedSettings": ["GENERIC_CITY"], "religious": false,
  "beats": [
    {"order": 1, "minPageCount": 10, "beat": "a"}, {"order": 2, "minPageCount": 10, "beat": "b"},
    {"order": 3, "minPageCount": 10, "beat": "c"}, {"order": 4, "minPageCount": 10, "beat": "d"},
    {"order": 5, "minPageCount": 10, "beat": "e"}, {"order": 6, "minPageCount": 10, "beat": "f"},
    {"order": 7, "minPageCount": 10, "beat": "g"}, {"order": 8, "minPageCount": 10, "beat": "h"},
    {"order": 9, "minPageCount": 10, "beat": "i"}, {"order": 10, "minPageCount": 10, "beat": "j"},
    {"order": 11, "minPageCount": 10, "beat": "k"}, {"order": 12, "minPageCount": 12, "beat": "l"},
    {"order": 13, "minPageCount": 15, "beat": "m"}, {"order": 14, "minPageCount": 15, "beat": "n"},
    {"order": 15, "minPageCount": 15, "beat": "o"}
  ]
}
```

- [ ] **Step 3: Write the failing test**

```java
package com.doova.ktab.features.storybook.blueprint;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintCatalogTest {

    private final BlueprintCatalog catalog =
            new BlueprintCatalog(new ObjectMapper(), "classpath*:storybook/blueprints/*.json");

    @Test
    void loadsTheSampleBlueprint() {
        Blueprint b = catalog.get("first-day-of-school");
        assertThat(b.version()).isEqualTo(1);
        assertThat(b.religious()).isFalse();
    }

    @Test
    void beatsForEachPageCountHaveExactlyThatManyBeatsInOrder() {
        Blueprint b = catalog.get("first-day-of-school");
        assertThat(b.beatsFor(10)).hasSize(10);
        assertThat(b.beatsFor(12)).hasSize(12);
        assertThat(b.beatsFor(15)).hasSize(15);
        assertThat(b.beatsFor(15)).extracting(BlueprintBeat::order).isSorted();
    }

    @Test
    void filtersByAgeBand() {
        assertThat(catalog.forAgeBand(AgeBand.AGE_3_5)).extracting(Blueprint::key).contains("first-day-of-school");
        assertThat(catalog.forAgeBand(AgeBand.AGE_9_10)).extracting(Blueprint::key).doesNotContain("first-day-of-school");
    }

    @Test
    void unknownKeyIsRejected() {
        assertThatThrownBy(() -> catalog.get("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidBlueprintFailsLoading() {
        assertThatThrownBy(() -> new BlueprintCatalog(new ObjectMapper(), "classpath*:storybook/blueprints-invalid/*.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broken");
    }

    @Test
    void unsupportedPageCountIsRejected() {
        assertThatThrownBy(() -> catalog.get("first-day-of-school").beatsFor(11))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=BlueprintCatalogTest`
Expected: COMPILATION ERROR — `BlueprintCatalog` does not exist.

- [ ] **Step 5: Implement**

`BlueprintBeat.java`:
```java
package com.doova.ktab.features.storybook.blueprint;

public record BlueprintBeat(int order, int minPageCount, String beat) {
}
```

`Blueprint.java`:
```java
package com.doova.ktab.features.storybook.blueprint;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.StorySetting;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record Blueprint(
        String key,
        int version,
        String titleAr,
        String titleEn,
        String theme,
        List<AgeBand> ageBands,
        List<StorySetting> allowedSettings,
        boolean religious,
        List<BlueprintBeat> beats
) {
    public static final Set<Integer> PAGE_COUNTS = Set.of(10, 12, 15);

    public List<BlueprintBeat> beatsFor(int pageCount) {
        if (!PAGE_COUNTS.contains(pageCount)) {
            throw new IllegalArgumentException("Page count must be 10, 12 or 15, was " + pageCount);
        }
        return beats.stream()
                .filter(b -> b.minPageCount() <= pageCount)
                .sorted(Comparator.comparingInt(BlueprintBeat::order))
                .toList();
    }
}
```

`BlueprintCatalog.java`:
```java
package com.doova.ktab.features.storybook.blueprint;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Component
public class BlueprintCatalog {

    private final Map<String, Blueprint> latestByKey = new HashMap<>();

    @Autowired
    public BlueprintCatalog(ObjectMapper objectMapper) {
        this(objectMapper, "classpath*:storybook/blueprints/*.json");
    }

    public BlueprintCatalog(ObjectMapper objectMapper, String locationPattern) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
            for (Resource resource : resources) {
                Blueprint blueprint;
                try (InputStream in = resource.getInputStream()) {
                    blueprint = objectMapper.readValue(in, Blueprint.class);
                }
                validate(blueprint, resource.getFilename());
                latestByKey.merge(blueprint.key(), blueprint,
                        (a, b) -> a.version() >= b.version() ? a : b);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load storybook blueprints from " + locationPattern, e);
        }
    }

    public Blueprint get(String key) {
        Blueprint blueprint = latestByKey.get(key);
        if (blueprint == null) {
            throw new IllegalArgumentException("Unknown blueprint: " + key);
        }
        return blueprint;
    }

    public List<Blueprint> forAgeBand(AgeBand band) {
        return all().stream().filter(b -> b.ageBands().contains(band)).toList();
    }

    public List<Blueprint> all() {
        return latestByKey.values().stream().sorted(Comparator.comparing(Blueprint::key)).toList();
    }

    private static void validate(Blueprint b, String filename) {
        String expected = b.key() + ".v" + b.version() + ".json";
        if (!expected.equals(filename)) {
            throw new IllegalStateException("Blueprint file " + filename + " must be named " + expected);
        }
        if (b.ageBands() == null || b.ageBands().isEmpty()) {
            throw new IllegalStateException("Blueprint " + b.key() + " has no age bands");
        }
        List<Integer> orders = b.beats().stream().map(BlueprintBeat::order).sorted().toList();
        if (!orders.equals(IntStream.rangeClosed(1, 15).boxed().toList())) {
            throw new IllegalStateException("Blueprint " + b.key() + " must have beat orders 1..15");
        }
        for (BlueprintBeat beat : b.beats()) {
            if (!Blueprint.PAGE_COUNTS.contains(beat.minPageCount())) {
                throw new IllegalStateException("Blueprint " + b.key() + " beat " + beat.order()
                        + " has minPageCount " + beat.minPageCount() + "; use 10, 12 or 15");
            }
        }
        for (int pageCount : List.of(10, 12, 15)) {
            int n = b.beatsFor(pageCount).size();
            if (n != pageCount) {
                throw new IllegalStateException("Blueprint " + b.key() + " yields " + n
                        + " beats for a " + pageCount + "-page book");
            }
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=BlueprintCatalogTest`
Expected: 6 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/blueprint src/main/resources/storybook/blueprints src/test/java/com/doova/ktab/features/storybook/blueprint src/test/resources/storybook/blueprints-invalid
git commit -m "feat(storybook): add validated blueprint catalog with sample blueprint"
```

---

### Task 8: Story request, prompt builder and `StoryWriter`

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/character/ChildAppearance.java`, `CompanionSpec.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/story/StoryRequest.java`, `StoryPlanResponse.java`, `PagePlan.java`, `CharacterInScene.java`, `StoryPromptBuilder.java`, `StoryWriter.java`, `StoryPlanInvalidException.java`
- Create (test support, reused by later sub-plans): `src/test/java/com/doova/ktab/features/storybook/support/FakeLlmGateway.java`, `StoryFixtures.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/story/StoryPromptBuilderTest.java`, `StoryWriterTest.java`

**Interfaces:**
- Consumes: `LlmGateway`, `LlmRequest`, `LlmCall` (Task 4); `PromptLibrary` (Task 6); `Blueprint` (Task 7); enums (Task 2).
- Produces:
  - `record ChildAppearance(SkinTone skinTone, HairColor hairColor, HairStyle hairStyle, EyeColor eyeColor, boolean hijab, boolean glasses)` with nested enums (each with `String en()`) and `String describeEn()`.
  - `record CompanionSpec(CompanionType type, String nameAr, ChildAppearance siblingAppearance, PetColor petColor)` with nested enums `CompanionType {BROTHER, SISTER, CAT, DOG, RABBIT, PARROT}` (`boolean isPet()`, `String en()`) and `PetColor` (`String en()`); `String describeEn()`.
  - `record StoryRequest(String childNameAr, ChildGender gender, AgeBand ageBand, ChildAppearance appearance, LanguageVariety variety, Blueprint blueprint, int pageCount, List<Interest> interests, CompanionSpec companion, StorySetting setting)` — `companion` and `setting` may be null.
  - `record StoryPlanResponse(String titleAr, String coverSceneEn, List<PagePlan> pages)`; `StoryPlanResponse withPage(PagePlan replacement)`.
  - `record PagePlan(int pageNumber, String textAr, String sceneEn, List<CharacterInScene> characters, TextZone textZone)`; `PagePlan withText(String newText)`.
  - `record CharacterInScene(String ref, String emotion)` — `ref` is `CHILD` or `COMPANION`.
  - `StoryPromptBuilder.planUserMessage(StoryRequest r, String dialectGuide)` and `rewriteUserMessage(StoryRequest r, String dialectGuide, PagePlan page, List<String> problems)` — static, pure.
  - `StoryWriter.writePlan(StoryRequest r) : LlmCall<StoryPlanResponse>` and `StoryWriter.rewritePage(StoryRequest r, PagePlan page, List<String> problems) : LlmCall<PagePlan>`.
  - `StoryPlanInvalidException extends LlmCallFailedException` (retryable = true).
  - Test support: `FakeLlmGateway implements LlmGateway` with `void enqueue(Object value)`, `List<LlmRequest<?>> requests()`; `StoryFixtures.request(LanguageVariety v, ChildGender g, int pageCount)` and `StoryFixtures.plan(int pageCount, String textPerPage)`.

- [ ] **Step 1: Write the character records**

`ChildAppearance.java`:
```java
package com.doova.ktab.features.storybook.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Avatar-builder output (spec, "Inputs"). Structured on purpose: no free text reaches the image model. */
public record ChildAppearance(
        SkinTone skinTone,
        HairColor hairColor,
        HairStyle hairStyle,
        EyeColor eyeColor,
        boolean hijab,
        boolean glasses
) {
    public ChildAppearance {
        Objects.requireNonNull(skinTone, "skinTone");
        Objects.requireNonNull(eyeColor, "eyeColor");
        if (!hijab) {
            Objects.requireNonNull(hairColor, "hairColor");
            Objects.requireNonNull(hairStyle, "hairStyle");
        }
    }

    public String describeEn() {
        List<String> parts = new ArrayList<>();
        parts.add(skinTone.en() + " skin");
        if (hijab) {
            parts.add("wears a simple plain hijab that fully covers the hair");
        } else {
            parts.add(hairColor.en() + " " + hairStyle.en() + " hair");
        }
        parts.add(eyeColor.en() + " eyes");
        if (glasses) {
            parts.add("round glasses");
        }
        return String.join(", ", parts);
    }

    public enum SkinTone {
        VERY_LIGHT("very light"), LIGHT("light"), LIGHT_OLIVE("light olive"), OLIVE("olive"),
        TAN("tan"), BROWN("brown"), DARK_BROWN("dark brown");
        private final String en;
        SkinTone(String en) { this.en = en; }
        public String en() { return en; }
    }

    public enum HairColor {
        BLACK("black"), DARK_BROWN("dark brown"), BROWN("brown"), LIGHT_BROWN("light brown"),
        BLONDE("blonde"), RED("red");
        private final String en;
        HairColor(String en) { this.en = en; }
        public String en() { return en; }
    }

    public enum HairStyle {
        VERY_SHORT("very short"), SHORT_STRAIGHT("short straight"), SHORT_CURLY("short curly"),
        MEDIUM_STRAIGHT("shoulder-length straight"), MEDIUM_CURLY("shoulder-length curly"),
        LONG_STRAIGHT("long straight"), LONG_CURLY("long curly"), PONYTAIL("ponytail"), BRAIDS("two braids");
        private final String en;
        HairStyle(String en) { this.en = en; }
        public String en() { return en; }
    }

    public enum EyeColor {
        DARK_BROWN("dark brown"), BROWN("brown"), HAZEL("hazel"), GREEN("green"), BLUE("blue"), GREY("grey");
        private final String en;
        EyeColor(String en) { this.en = en; }
        public String en() { return en; }
    }
}
```

`CompanionSpec.java`:
```java
package com.doova.ktab.features.storybook.character;

import java.util.Objects;

/** One companion at most in the MVP (decision D5). */
public record CompanionSpec(CompanionType type, String nameAr, ChildAppearance siblingAppearance, PetColor petColor) {

    public CompanionSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(nameAr, "nameAr");
        if (type.isPet()) {
            Objects.requireNonNull(petColor, "petColor is required for a pet");
        } else {
            Objects.requireNonNull(siblingAppearance, "siblingAppearance is required for a sibling");
        }
    }

    public String describeEn() {
        return type.isPet()
                ? "a " + petColor.en() + " " + type.en()
                : "the child's " + type.en() + ": " + siblingAppearance.describeEn();
    }

    public enum CompanionType {
        BROTHER("younger brother", false), SISTER("younger sister", false),
        CAT("cat", true), DOG("dog", true), RABBIT("rabbit", true), PARROT("parrot", true);
        private final String en;
        private final boolean pet;
        CompanionType(String en, boolean pet) { this.en = en; this.pet = pet; }
        public String en() { return en; }
        public boolean isPet() { return pet; }
    }

    public enum PetColor {
        WHITE("white"), BLACK("black"), GREY("grey"), ORANGE("orange"), BROWN("brown"),
        BLACK_AND_WHITE("black-and-white"), GREEN("green");
        private final String en;
        PetColor(String en) { this.en = en; }
        public String en() { return en; }
    }
}
```

- [ ] **Step 2: Write the story records**

`CharacterInScene.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record CharacterInScene(
        @JsonPropertyDescription("CHILD or COMPANION") String ref,
        @JsonPropertyDescription("The character's emotion on this page, in English, e.g. excited, shy, proud") String emotion
) {
}
```

`PagePlan.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.TextZone;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record PagePlan(
        @JsonPropertyDescription("1-based page number") int pageNumber,
        @JsonPropertyDescription("The Arabic text printed on this page") String textAr,
        @JsonPropertyDescription("English description of the illustration; characters named CHILD and COMPANION; no text in the picture") String sceneEn,
        List<CharacterInScene> characters,
        @JsonPropertyDescription("Which third of the picture is left calm and empty for the text") TextZone textZone
) {
    public PagePlan withText(String newText) {
        return new PagePlan(pageNumber, newText, sceneEn, characters, textZone);
    }
}
```

`StoryPlanResponse.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record StoryPlanResponse(
        @JsonPropertyDescription("Book title, 2 to 5 words, same language variety as the pages") String titleAr,
        @JsonPropertyDescription("English description of the cover illustration; calm empty TOP third for the title") String coverSceneEn,
        List<PagePlan> pages
) {
    public StoryPlanResponse withPage(PagePlan replacement) {
        return new StoryPlanResponse(titleAr, coverSceneEn, pages.stream()
                .map(p -> p.pageNumber() == replacement.pageNumber() ? replacement : p)
                .toList());
    }
}
```

`StoryRequest.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.blueprint.Blueprint;
import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;

import java.util.List;
import java.util.Objects;

public record StoryRequest(
        String childNameAr,
        ChildGender gender,
        AgeBand ageBand,
        ChildAppearance appearance,
        LanguageVariety variety,
        Blueprint blueprint,
        int pageCount,
        List<Interest> interests,
        CompanionSpec companion,
        StorySetting setting
) {
    public StoryRequest {
        Objects.requireNonNull(childNameAr, "childNameAr");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(ageBand, "ageBand");
        Objects.requireNonNull(variety, "variety");
        Objects.requireNonNull(blueprint, "blueprint");
        interests = interests == null ? List.of() : List.copyOf(interests);
    }
}
```

`StoryPlanInvalidException.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.llm.LlmCallFailedException;

/** The model returned well-formed JSON that breaks the book's structure; a fresh attempt usually fixes it. */
public class StoryPlanInvalidException extends LlmCallFailedException {
    public StoryPlanInvalidException(String message) {
        super(message, true, null);
    }
}
```

- [ ] **Step 3: Write the test support classes**

`src/test/java/com/doova/ktab/features/storybook/support/FakeLlmGateway.java`:
```java
package com.doova.ktab.features.storybook.support;

import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Returns queued values in order and records every request. */
public class FakeLlmGateway implements LlmGateway {

    private final Deque<Object> responses = new ArrayDeque<>();
    private final List<LlmRequest<?>> requests = new ArrayList<>();

    public void enqueue(Object value) {
        responses.addLast(value);
    }

    public List<LlmRequest<?>> requests() {
        return requests;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> LlmCall<T> call(LlmRequest<T> request) {
        requests.add(request);
        if (responses.isEmpty()) {
            throw new IllegalStateException("FakeLlmGateway: no response queued for " + request.purpose());
        }
        Object value = responses.removeFirst();
        if (value instanceof RuntimeException e) {
            throw e;
        }
        return new LlmCall<>((T) value, "claude-sonnet-5", 1_000, 500, 5);
    }
}
```

`src/test/java/com/doova/ktab/features/storybook/support/StoryFixtures.java`:
```java
package com.doova.ktab.features.storybook.support;

import com.doova.ktab.features.storybook.blueprint.BlueprintCatalog;
import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.IntStream;

public final class StoryFixtures {

    public static final BlueprintCatalog CATALOG = new BlueprintCatalog(new ObjectMapper());

    public static final ChildAppearance APPEARANCE = new ChildAppearance(
            ChildAppearance.SkinTone.OLIVE, ChildAppearance.HairColor.BLACK,
            ChildAppearance.HairStyle.SHORT_CURLY, ChildAppearance.EyeColor.BROWN, false, false);

    private StoryFixtures() {
    }

    public static StoryRequest request(LanguageVariety variety, ChildGender gender, int pageCount) {
        return new StoryRequest("سامي", gender, AgeBand.AGE_6_8, APPEARANCE, variety,
                CATALOG.get("first-day-of-school"), pageCount, List.of(Interest.FOOTBALL), null, StorySetting.BEIRUT);
    }

    public static StoryPlanResponse plan(int pageCount, String textPerPage) {
        List<PagePlan> pages = IntStream.rangeClosed(1, pageCount)
                .mapToObj(n -> new PagePlan(n, textPerPage, "The CHILD smiles in a sunny classroom.",
                        List.of(new CharacterInScene("CHILD", "happy")), TextZone.TOP))
                .toList();
        return new StoryPlanResponse("يومي الأول", "The CHILD at the school gate.", pages);
    }
}
```

- [ ] **Step 4: Write the failing tests**

`StoryPromptBuilderTest.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoryPromptBuilderTest {

    @Test
    void msaRequestAsksForFullVocalizationAndCarriesTheDetails() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.GIRL, 10);

        String msg = StoryPromptBuilder.planUserMessage(r, "");

        assertThat(msg).contains("«سامي»")
                .contains("girl")
                .contains("at most 45 words")
                .contains("fully vocalized")
                .contains("playing football")
                .contains("Beirut");
        assertThat(msg).contains("Page 10:").doesNotContain("Page 11:");
    }

    @Test
    void dialectRequestForbidsTashkeelAndIncludesTheGuide() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.LEBANESE, ChildGender.BOY, 12);

        String msg = StoryPromptBuilder.planUserMessage(r, "# Lebanese dialect style guide\n1. No tashkeel.");

        assertThat(msg).contains("Lebanese Arabic").contains("no tashkeel").contains("# Lebanese dialect style guide");
        assertThat(msg).doesNotContain("fully vocalized");
    }

    @Test
    void companionIsDescribedWhenPresent() {
        StoryRequest base = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        StoryRequest r = new StoryRequest(base.childNameAr(), base.gender(), base.ageBand(), base.appearance(),
                base.variety(), base.blueprint(), base.pageCount(), base.interests(),
                new CompanionSpec(CompanionSpec.CompanionType.CAT, "بسبوسة", null, CompanionSpec.PetColor.ORANGE),
                base.setting());

        assertThat(StoryPromptBuilder.planUserMessage(r, "")).contains("«بسبوسة»").contains("orange cat");
    }

    @Test
    void rewriteMessageListsTheProblems() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.GIRL, 10);
        PagePlan page = StoryFixtures.plan(10, "ذهبَ سامي").pages().get(2);

        String msg = StoryPromptBuilder.rewriteUserMessage(r, "", page, List.of("verb ذهبَ should be feminine"));

        assertThat(msg).contains("Page 3").contains("verb ذهبَ should be feminine").contains("ذهبَ سامي");
    }
}
```

`StoryWriterTest.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryWriterTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final StoryWriter writer = new StoryWriter(llm, new PromptLibrary());

    @Test
    void returnsThePlanAndUsesTheStaticSystemPrompt() {
        llm.enqueue(StoryFixtures.plan(10, "نَصٌّ"));

        LlmCall<StoryPlanResponse> call = writer.writePlan(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10));

        assertThat(call.value().pages()).hasSize(10);
        assertThat(llm.requests()).singleElement().satisfies(r -> {
            assertThat(r.purpose()).isEqualTo(LlmPurpose.STORY_PLAN);
            assertThat(r.system()).startsWith("You write personalized Arabic picture books");
            assertThat(r.responseType()).isEqualTo(StoryPlanResponse.class);
        });
    }

    @Test
    void rejectsWrongPageCount() {
        llm.enqueue(StoryFixtures.plan(9, "نَصٌّ"));

        assertThatThrownBy(() -> writer.writePlan(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10)))
                .isInstanceOf(StoryPlanInvalidException.class)
                .hasMessageContaining("expected 10");
    }

    @Test
    void rejectsPagesNotNumberedOneToN() {
        StoryPlanResponse zeroBased = new StoryPlanResponse("t", "c", StoryFixtures.plan(10, "نَصٌّ").pages().stream()
                .map(p -> new PagePlan(p.pageNumber() - 1, p.textAr(), p.sceneEn(), p.characters(), p.textZone()))
                .toList());
        llm.enqueue(zeroBased);

        assertThatThrownBy(() -> writer.writePlan(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10)))
                .isInstanceOf(StoryPlanInvalidException.class);
    }

    @Test
    void rewriteKeepsThePageNumberEvenIfTheModelChangesIt() {
        PagePlan original = StoryFixtures.plan(10, "ذهبَ").pages().get(4);
        llm.enqueue(new PagePlan(99, "ذهبتْ", original.sceneEn(), original.characters(), original.textZone()));

        LlmCall<PagePlan> call = writer.rewritePage(StoryFixtures.request(LanguageVariety.MSA, ChildGender.GIRL, 10),
                original, List.of("gender"));

        assertThat(call.value().pageNumber()).isEqualTo(5);
        assertThat(call.value().textAr()).isEqualTo("ذهبتْ");
        assertThat(llm.requests().get(0).purpose()).isEqualTo(LlmPurpose.STORY_PAGE_REWRITE);
    }
}
```

- [ ] **Step 5: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='StoryPromptBuilderTest,StoryWriterTest'`
Expected: COMPILATION ERROR — `StoryPromptBuilder` and `StoryWriter` do not exist.

- [ ] **Step 6: Implement `StoryPromptBuilder`**

```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.blueprint.BlueprintBeat;
import com.doova.ktab.features.storybook.enums.Interest;

import java.util.List;
import java.util.stream.Collectors;

/** Builds the per-book user messages. The system prompts stay static so they can be cached. */
public final class StoryPromptBuilder {

    private StoryPromptBuilder() {
    }

    public static String planUserMessage(StoryRequest r, String dialectGuide) {
        StringBuilder sb = new StringBuilder();
        appendChildAndLanguage(sb, r, dialectGuide);
        sb.append("\nBlueprint: ").append(r.blueprint().titleEn())
                .append(" (").append(r.pageCount()).append(" pages, one beat per page)\n");
        List<BlueprintBeat> beats = r.blueprint().beatsFor(r.pageCount());
        for (int i = 0; i < beats.size(); i++) {
            sb.append("Page ").append(i + 1).append(": ").append(beats.get(i).beat()).append('\n');
        }
        sb.append("\nReturn exactly ").append(r.pageCount())
                .append(" pages numbered 1 to ").append(r.pageCount()).append(", plus the title and cover scene.");
        return sb.toString();
    }

    public static String rewriteUserMessage(StoryRequest r, String dialectGuide, PagePlan page, List<String> problems) {
        StringBuilder sb = new StringBuilder();
        appendChildAndLanguage(sb, r, dialectGuide);
        sb.append("\nPage ").append(page.pageNumber()).append(" currently reads:\n").append(page.textAr()).append('\n');
        sb.append("Its scene: ").append(page.sceneEn()).append('\n');
        sb.append("\nProblems the editor found:\n");
        problems.forEach(p -> sb.append("- ").append(p).append('\n'));
        return sb.toString();
    }

    private static void appendChildAndLanguage(StringBuilder sb, StoryRequest r, String dialectGuide) {
        sb.append("The child\n");
        sb.append("- Name (write exactly like this): «").append(r.childNameAr()).append("»\n");
        sb.append("- A ").append(r.gender().en()).append(" aged ")
                .append(r.ageBand().minAge()).append(" to ").append(r.ageBand().maxAge()).append('\n');
        if (!r.interests().isEmpty()) {
            sb.append("- Loves: ").append(r.interests().stream().map(Interest::en)
                    .collect(Collectors.joining(", "))).append('\n');
        }
        if (r.setting() != null) {
            sb.append("- Setting: ").append(r.setting().sceneEn()).append('\n');
        }
        if (r.companion() != null) {
            sb.append("- Companion (COMPANION in scenes), named «").append(r.companion().nameAr()).append("»: ")
                    .append(r.companion().describeEn()).append('\n');
        }
        sb.append("\nLanguage\n");
        if (r.variety().isDialect()) {
            sb.append("- Write in ").append(r.variety().en())
                    .append(" with no tashkeel at all, following this style guide exactly:\n\n")
                    .append(dialectGuide).append("\n\n");
        } else {
            sb.append("- Write in Modern Standard Arabic, fully vocalized.\n");
        }
        sb.append("- Each page: 1 to 4 sentences, at most ").append(r.ageBand().maxWordsPerPage()).append(" words.\n");
    }
}
```

- [ ] **Step 7: Implement `StoryWriter`**

```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmRequest;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StoryWriter {

    private final LlmGateway llm;
    private final PromptLibrary prompts;

    public LlmCall<StoryPlanResponse> writePlan(StoryRequest r) {
        String user = StoryPromptBuilder.planUserMessage(r, prompts.dialectGuide(r.variety()));
        LlmCall<StoryPlanResponse> call = llm.call(LlmRequest.of(LlmPurpose.STORY_PLAN,
                prompts.get("story-plan-system"), user, StoryPlanResponse.class));
        validate(call.value(), r.pageCount());
        return call;
    }

    public LlmCall<PagePlan> rewritePage(StoryRequest r, PagePlan page, List<String> problems) {
        String user = StoryPromptBuilder.rewriteUserMessage(r, prompts.dialectGuide(r.variety()), page, problems);
        LlmCall<PagePlan> call = llm.call(LlmRequest.of(LlmPurpose.STORY_PAGE_REWRITE,
                prompts.get("page-rewrite-system"), user, PagePlan.class));
        PagePlan v = call.value();
        if (v.textAr() == null || v.textAr().isBlank()) {
            throw new StoryPlanInvalidException("Rewrite of page " + page.pageNumber() + " returned empty text");
        }
        // The page number is ours, not the model's.
        PagePlan fixed = new PagePlan(page.pageNumber(), v.textAr(), v.sceneEn(), v.characters(), v.textZone());
        return new LlmCall<>(fixed, call.model(), call.inputTokens(), call.outputTokens(), call.latencyMs());
    }

    private static void validate(StoryPlanResponse plan, int pageCount) {
        if (plan.titleAr() == null || plan.titleAr().isBlank()) {
            throw new StoryPlanInvalidException("Story plan has no title");
        }
        if (plan.pages() == null || plan.pages().size() != pageCount) {
            throw new StoryPlanInvalidException("Story plan has " + (plan.pages() == null ? 0 : plan.pages().size())
                    + " pages, expected " + pageCount);
        }
        for (int i = 0; i < pageCount; i++) {
            PagePlan p = plan.pages().get(i);
            if (p.pageNumber() != i + 1 || p.textAr() == null || p.textAr().isBlank()
                    || p.sceneEn() == null || p.sceneEn().isBlank() || p.textZone() == null) {
                throw new StoryPlanInvalidException("Story plan page at position " + (i + 1) + " is malformed");
            }
        }
    }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='StoryPromptBuilderTest,StoryWriterTest'`
Expected: 8 tests PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/character src/main/java/com/doova/ktab/features/storybook/story src/test/java/com/doova/ktab/features/storybook/support src/test/java/com/doova/ktab/features/storybook/story
git commit -m "feat(storybook): add story request model, prompt builder and story writer"
```

---

### Task 9: Arabic text utilities — tashkeel filter, name enforcement, deterministic checks

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/story/ArabicText.java`, `TashkeelFilter.java`, `NameEnforcer.java`, `DeterministicTextChecks.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/story/TashkeelFilterTest.java`, `NameEnforcerTest.java`, `DeterministicTextChecksTest.java`

**Interfaces:**
- Consumes: `StoryRequest`, `PagePlan` (Task 8); `TashkeelLevel`, `LanguageVariety` (Task 2).
- Produces:
  - `ArabicText.isTashkeel(char c)`, `ArabicText.stripTashkeel(String s)`, `ArabicText.hasLatinLetters(String s)`, `ArabicText.wordCount(String s)`, `ArabicText.sentenceCount(String s)`.
  - `TashkeelFilter.apply(String text, TashkeelLevel level)` — FULL unchanged; NONE strips all; PARTIAL keeps shadda (U+0651) and tanween (U+064B–U+064D) only (decision D3).
  - `NameEnforcer.enforce(String text, String typedName)` — rewrites every occurrence of the name that matches ignoring tashkeel and alif/ta-marbuta/alif-maqsura variants (with an optional one-letter proclitic و ف ب ل ك) to the exact typed name; returns the new text.
  - `DeterministicTextChecks.check(StoryRequest r, PagePlan page)` → `List<String>` problems (empty = pass).

Tashkeel code points (Unicode Arabic block): U+064B fathatan, U+064C dammatan, U+064D kasratan, U+064E fatha, U+064F damma, U+0650 kasra, U+0651 shadda, U+0652 sukun, U+0670 superscript alef.

- [ ] **Step 1: Write the failing tests**

`TashkeelFilterTest.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TashkeelFilterTest {

    private static final String VOCALIZED = "ذَهَبَ مُحَمَّدٌ إِلَى المَدْرَسَةِ";

    @Test
    void fullKeepsEverything() {
        assertThat(TashkeelFilter.apply(VOCALIZED, TashkeelLevel.FULL)).isEqualTo(VOCALIZED);
    }

    @Test
    void noneStripsEveryMark() {
        assertThat(TashkeelFilter.apply(VOCALIZED, TashkeelLevel.NONE)).isEqualTo("ذهب محمد إلى المدرسة");
    }

    @Test
    void partialKeepsOnlyShaddaAndTanween() {
        // مُحَمَّدٌ keeps shadda (ّ) and dammatan (ٌ); every fatha/damma/kasra/sukun goes.
        assertThat(TashkeelFilter.apply(VOCALIZED, TashkeelLevel.PARTIAL)).isEqualTo("ذهب محمّدٌ إلى المدرسة");
    }

    @Test
    void lettersWithHamzaAreUntouched() {
        assertThat(TashkeelFilter.apply("أُمّي", TashkeelLevel.NONE)).isEqualTo("أمي");
    }
}
```

`NameEnforcerTest.java`:
```java
package com.doova.ktab.features.storybook.story;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameEnforcerTest {

    @Test
    void replacesAVocalizedNameWithTheTypedOne() {
        assertThat(NameEnforcer.enforce("ذَهَبَ مُحَمَّدٌ إِلَى البَيْتِ.", "محمد"))
                .isEqualTo("ذَهَبَ محمد إِلَى البَيْتِ.");
    }

    @Test
    void keepsTheParentsTashkeelWhenTheyTypedIt() {
        assertThat(NameEnforcer.enforce("قال محمد: «مرحبا!»", "مُحَمَّد"))
                .isEqualTo("قال مُحَمَّد: «مرحبا!»");
    }

    @Test
    void fixesAlifVariants() {
        assertThat(NameEnforcer.enforce("لعب احمد مع أحمدَ", "أحمد")).isEqualTo("لعب أحمد مع أحمد");
    }

    @Test
    void fixesTaMarbutaVariant() {
        assertThat(NameEnforcer.enforce("ضحكت فاطمه", "فاطمة")).isEqualTo("ضحكت فاطمة");
    }

    @Test
    void keepsAProcliticAttached() {
        assertThat(NameEnforcer.enforce("ولِمُحَمَّدٍ صديقٌ", "محمد")).isEqualTo("ولمحمد صديقٌ");
    }

    @Test
    void doesNotTouchLongerWordsThatContainTheName() {
        assertThat(NameEnforcer.enforce("المحمدية مدينة", "محمد")).isEqualTo("المحمدية مدينة");
    }
}
```

Note on the proclitic case: `ولِمُحَمَّدٍ` is و (conjunction) + ل (preposition) + the name. The enforcer therefore allows an optional conjunction `[وف]` followed by an optional preposition `[بلك]`, each with optional tashkeel, keeps them (tashkeel stripped) and replaces only the name part.

`DeterministicTextChecksTest.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicTextChecksTest {

    private static PagePlan page(String text) {
        return new PagePlan(1, text, "scene", List.of(), TextZone.TOP);
    }

    @Test
    void aGoodMsaPagePasses() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("ذَهَبَ سامي إِلَى المَدْرَسَةِ. كانَ سَعِيدًا."))).isEmpty();
    }

    @Test
    void tooManyWordsFails() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10); // age 6-8: max 45
        String long46 = "كلمة ".repeat(46).trim() + ".";
        assertThat(DeterministicTextChecks.check(r, page(long46))).anyMatch(p -> p.contains("46 words"));
    }

    @Test
    void moreThanFourSentencesFails() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("أ. ب. ج. د. هـ.")))
                .anyMatch(p -> p.contains("5 sentences"));
    }

    @Test
    void latinLettersFail() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("لعب سامي football.")))
                .anyMatch(p -> p.contains("Latin"));
    }

    @Test
    void tashkeelInADialectFails() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.EGYPTIAN, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("سامي راحَ المدرسة.")))
                .anyMatch(p -> p.contains("tashkeel"));
    }

    @Test
    void dialectWithoutTashkeelPasses() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.EGYPTIAN, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("سامي راح المدرسة.")))
                .isEmpty();
    }

    @Test
    void emptyTextFails() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("  "))).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='TashkeelFilterTest,NameEnforcerTest,DeterministicTextChecksTest'`
Expected: COMPILATION ERROR — the classes do not exist.

- [ ] **Step 3: Implement `ArabicText`**

```java
package com.doova.ktab.features.storybook.story;

import java.util.Arrays;
import java.util.regex.Pattern;

public final class ArabicText {

    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!؟?]+");

    private ArabicText() {
    }

    public static boolean isTashkeel(char c) {
        return (c >= 0x064B && c <= 0x0652) || c == 0x0670;
    }

    public static boolean isTanween(char c) {
        return c >= 0x064B && c <= 0x064D;
    }

    public static boolean isShadda(char c) {
        return c == 0x0651;
    }

    public static String stripTashkeel(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (!isTashkeel(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean containsTashkeel(String s) {
        for (char c : s.toCharArray()) {
            if (isTashkeel(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasLatinLetters(String s) {
        return LATIN.matcher(s).find();
    }

    public static int wordCount(String s) {
        String trimmed = s.strip();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    public static int sentenceCount(String s) {
        return (int) Arrays.stream(SENTENCE_END.split(s.strip()))
                .filter(part -> !part.isBlank())
                .count();
    }
}
```

- [ ] **Step 4: Implement `TashkeelFilter`**

```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.TashkeelLevel;

/** Applied at render time; stored text is always the fully vocalized version (decision D3). */
public final class TashkeelFilter {

    private TashkeelFilter() {
    }

    public static String apply(String text, TashkeelLevel level) {
        return switch (level) {
            case FULL -> text;
            case NONE -> ArabicText.stripTashkeel(text);
            case PARTIAL -> {
                StringBuilder sb = new StringBuilder(text.length());
                for (char c : text.toCharArray()) {
                    if (!ArabicText.isTashkeel(c) || ArabicText.isShadda(c) || ArabicText.isTanween(c)) {
                        sb.append(c);
                    }
                }
                yield sb.toString();
            }
        };
    }
}
```

- [ ] **Step 5: Implement `NameEnforcer`**

```java
package com.doova.ktab.features.storybook.story;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes every mention of the child's name byte-identical to what the parent typed
 * (spec: "written exactly as the parent typed it"), instead of rejecting pages where
 * the model vocalized the name or picked a different alif.
 */
public final class NameEnforcer {

    private static final String MARKS = "[\\u064B-\\u0652\\u0670]*";
    private static final String PROCLITIC = "((?:[وف]" + MARKS + ")?(?:[بلك]" + MARKS + ")?)";
    private static final String NOT_LETTER_BEFORE = "(?<![\\p{L}\\p{M}])";
    private static final String NOT_LETTER_AFTER = "(?![\\p{L}\\p{M}])";

    private NameEnforcer() {
    }

    public static String enforce(String text, String typedName) {
        Pattern pattern = Pattern.compile(NOT_LETTER_BEFORE + PROCLITIC + namePattern(typedName) + NOT_LETTER_AFTER);
        Matcher m = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String proclitic = ArabicText.stripTashkeel(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(proclitic + typedName));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String namePattern(String typedName) {
        String bare = ArabicText.stripTashkeel(typedName);
        StringBuilder sb = new StringBuilder("(?:");
        for (int i = 0; i < bare.length(); i++) {
            char c = bare.charAt(i);
            sb.append(letterClass(c, i == bare.length() - 1)).append(MARKS);
        }
        return sb.append(')').toString();
    }

    private static String letterClass(char c, boolean last) {
        return switch (c) {
            case 'ا', 'أ', 'إ', 'آ', 'ٱ' -> "[اأإآٱ]";
            case 'ة', 'ه' -> last ? "[ةه]" : Pattern.quote(String.valueOf(c));
            case 'ى', 'ي' -> last ? "[ىي]" : Pattern.quote(String.valueOf(c));
            default -> Pattern.quote(String.valueOf(c));
        };
    }
}
```

- [ ] **Step 6: Implement `DeterministicTextChecks`**

```java
package com.doova.ktab.features.storybook.story;

import java.util.ArrayList;
import java.util.List;

/** The checks that do not need an LLM. The critic (Task 10) runs these first. */
public final class DeterministicTextChecks {

    private DeterministicTextChecks() {
    }

    public static List<String> check(StoryRequest r, PagePlan page) {
        List<String> problems = new ArrayList<>();
        String text = page.textAr() == null ? "" : page.textAr();
        if (text.isBlank()) {
            problems.add("Page " + page.pageNumber() + " has no text");
            return problems;
        }
        int words = ArabicText.wordCount(text);
        if (words > r.ageBand().maxWordsPerPage()) {
            problems.add("Page " + page.pageNumber() + " has " + words + " words; the limit for ages "
                    + r.ageBand().minAge() + "-" + r.ageBand().maxAge() + " is " + r.ageBand().maxWordsPerPage());
        }
        int sentences = ArabicText.sentenceCount(text);
        if (sentences < 1 || sentences > 4) {
            problems.add("Page " + page.pageNumber() + " has " + sentences + " sentences; it must have 1 to 4");
        }
        if (ArabicText.hasLatinLetters(text)) {
            problems.add("Page " + page.pageNumber() + " contains Latin letters");
        }
        if (r.variety().isDialect() && ArabicText.containsTashkeel(text)) {
            problems.add("Page " + page.pageNumber() + " is in " + r.variety().en()
                    + " but contains tashkeel; dialect text must have none");
        }
        return problems;
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='TashkeelFilterTest,NameEnforcerTest,DeterministicTextChecksTest'`
Expected: 17 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/story src/test/java/com/doova/ktab/features/storybook/story
git commit -m "feat(storybook): add tashkeel filter, name enforcement and deterministic text checks"
```

---

### Task 10: `StoryCritic`

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/story/CriticResponse.java`, `PageVerdict.java`, `CriticReport.java`, `StoryCritic.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/story/StoryCriticTest.java`

**Interfaces:**
- Consumes: `LlmGateway` (Task 4), `PromptLibrary` (Task 6), `StoryRequest`/`StoryPlanResponse`/`PagePlan` (Task 8), `NameEnforcer`/`DeterministicTextChecks` (Task 9).
- Produces:
  - `record CriticResponse(List<PageVerdict> pages)`; `record PageVerdict(int pageNumber, boolean pass, List<String> problems)` — page 0 is the title.
  - `record CriticReport(StoryPlanResponse plan, Map<Integer, List<String>> problemsByPage, LlmCall<CriticResponse> llmCall)` with `boolean allPass()` and `List<Integer> failingPages()`. `plan` is the name-enforced plan; `problemsByPage` only contains failing pages.
  - `StoryCritic.review(StoryRequest r, StoryPlanResponse plan) : CriticReport`.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class StoryCriticTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final StoryCritic critic = new StoryCritic(llm, new PromptLibrary());

    private static CriticResponse allPass(int pages) {
        return new CriticResponse(IntStream.rangeClosed(0, pages)
                .mapToObj(n -> new PageVerdict(n, true, List.of())).toList());
    }

    @Test
    void cleanBookPasses() {
        llm.enqueue(allPass(10));
        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10),
                StoryFixtures.plan(10, "ذَهَبَ سامي إِلَى المَدْرَسَةِ."));

        assertThat(report.allPass()).isTrue();
        assertThat(llm.requests()).singleElement()
                .satisfies(r -> assertThat(r.purpose()).isEqualTo(LlmPurpose.STORY_CRITIC));
    }

    @Test
    void llmVerdictFailsOnlyThatPage() {
        List<PageVerdict> verdicts = new java.util.ArrayList<>(allPass(10).pages());
        verdicts.set(3, new PageVerdict(3, false, List.of("ذهبَ should be ذهبتْ for a girl")));
        llm.enqueue(new CriticResponse(verdicts));

        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.GIRL, 10),
                StoryFixtures.plan(10, "ذَهَبَتْ سامي إِلَى المَدْرَسَةِ."));

        assertThat(report.failingPages()).containsExactly(3);
        assertThat(report.problemsByPage().get(3)).containsExactly("ذهبَ should be ذهبتْ for a girl");
    }

    @Test
    void deterministicProblemsAreMergedWithLlmVerdicts() {
        llm.enqueue(allPass(10));
        StoryPlanResponse plan = StoryFixtures.plan(10, "ذَهَبَ سامي.");
        plan = plan.withPage(plan.pages().get(1).withText("لعب سامي football."));

        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10), plan);

        assertThat(report.failingPages()).containsExactly(2);
        assertThat(report.problemsByPage().get(2)).anyMatch(p -> p.contains("Latin"));
    }

    @Test
    void theNameIsEnforcedBeforeReview() {
        llm.enqueue(allPass(10));
        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10),
                StoryFixtures.plan(10, "ذَهَبَ سَامِي إِلَى البَيْتِ."));

        assertThat(report.plan().pages()).allSatisfy(p -> assertThat(p.textAr()).contains("سامي"));
        assertThat(llm.requests().get(0).user()).doesNotContain("سَامِي");
    }

    @Test
    void aMissingVerdictCountsAsFailure() {
        llm.enqueue(new CriticResponse(List.of(new PageVerdict(0, true, List.of()))));
        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10),
                StoryFixtures.plan(10, "ذَهَبَ سامي."));

        assertThat(report.failingPages()).hasSize(10);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StoryCriticTest`
Expected: COMPILATION ERROR — `StoryCritic` does not exist.

- [ ] **Step 3: Implement the records**

`PageVerdict.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record PageVerdict(
        @JsonPropertyDescription("Page number; 0 is the title") int pageNumber,
        boolean pass,
        @JsonPropertyDescription("One short English sentence per problem, quoting the Arabic words") List<String> problems
) {
}
```

`CriticResponse.java`:
```java
package com.doova.ktab.features.storybook.story;

import java.util.List;

public record CriticResponse(List<PageVerdict> pages) {
}
```

`CriticReport.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.llm.LlmCall;

import java.util.List;
import java.util.Map;

public record CriticReport(StoryPlanResponse plan, Map<Integer, List<String>> problemsByPage,
                           LlmCall<CriticResponse> llmCall) {

    public boolean allPass() {
        return problemsByPage.isEmpty();
    }

    public List<Integer> failingPages() {
        return problemsByPage.keySet().stream().sorted().toList();
    }
}
```

- [ ] **Step 4: Implement `StoryCritic`**

```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmRequest;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class StoryCritic {

    private final LlmGateway llm;
    private final PromptLibrary prompts;

    public CriticReport review(StoryRequest r, StoryPlanResponse plan) {
        StoryPlanResponse enforced = new StoryPlanResponse(
                NameEnforcer.enforce(plan.titleAr(), r.childNameAr()),
                plan.coverSceneEn(),
                plan.pages().stream()
                        .map(p -> p.withText(NameEnforcer.enforce(p.textAr(), r.childNameAr())))
                        .toList());

        Map<Integer, List<String>> problems = new TreeMap<>();
        for (PagePlan page : enforced.pages()) {
            List<String> found = DeterministicTextChecks.check(r, page);
            if (!found.isEmpty()) {
                problems.put(page.pageNumber(), new ArrayList<>(found));
            }
        }

        LlmCall<CriticResponse> call = llm.call(LlmRequest.of(LlmPurpose.STORY_CRITIC,
                prompts.get("critic-system"), userMessage(r, enforced), CriticResponse.class));

        Map<Integer, PageVerdict> verdicts = new HashMap<>();
        if (call.value().pages() != null) {
            call.value().pages().forEach(v -> verdicts.put(v.pageNumber(), v));
        }
        for (int n = 0; n <= enforced.pages().size(); n++) {
            PageVerdict v = verdicts.get(n);
            if (v == null) {
                problems.computeIfAbsent(n, k -> new ArrayList<>()).add("The editor returned no verdict for this page");
            } else if (!v.pass()) {
                List<String> list = problems.computeIfAbsent(n, k -> new ArrayList<>());
                list.addAll(v.problems() == null || v.problems().isEmpty()
                        ? List.of("The editor failed this page without a reason") : v.problems());
            }
        }
        return new CriticReport(enforced, problems, call);
    }

    private String userMessage(StoryRequest r, StoryPlanResponse plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("The child is a ").append(r.gender().en()).append(" aged ")
                .append(r.ageBand().minAge()).append(" to ").append(r.ageBand().maxAge())
                .append(", named «").append(r.childNameAr()).append("».\n");
        if (r.variety().isDialect()) {
            sb.append("The book is written in ").append(r.variety().en())
                    .append(" and must follow this style guide:\n\n").append(prompts.dialectGuide(r.variety())).append("\n\n");
        } else {
            sb.append("The book is written in fully vocalized Modern Standard Arabic.\n\n");
        }
        sb.append("Page 0 (title): ").append(plan.titleAr()).append('\n');
        for (PagePlan p : plan.pages()) {
            sb.append("Page ").append(p.pageNumber()).append(": ").append(p.textAr()).append('\n');
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=StoryCriticTest`
Expected: 5 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/story src/test/java/com/doova/ktab/features/storybook/story
git commit -m "feat(storybook): add story critic merging deterministic and LLM checks"
```

---

### Task 11: `ModerationService`

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/story/ModerationResponse.java`, `ModerationService.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/story/ModerationServiceTest.java`

**Interfaces:**
- Consumes: `LlmGateway`, `PromptLibrary`, `StorybookProperties.getLimits().getDedicationMaxChars()`.
- Produces: `record ModerationResponse(boolean allowed, String reason)`; `ModerationService.moderate(String text) : ModerationOutcome` where `record ModerationOutcome(boolean allowed, String reason, LlmCall<ModerationResponse> llmCall)` — `llmCall` is `null` when a deterministic rule rejected the text without calling the LLM.

Deterministic rules applied before the LLM: blank → allowed (dedication is optional); longer than the configured maximum; contains `http`, `www.`, `@`, or 7+ digits in a row (Latin or Arabic-Indic).

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModerationServiceTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final ModerationService service = new ModerationService(llm, new PromptLibrary(), new StorybookProperties());

    @Test
    void blankIsAllowedWithoutCallingTheLlm() {
        assertThat(service.moderate("  ").allowed()).isTrue();
        assertThat(llm.requests()).isEmpty();
    }

    @Test
    void phoneNumbersAreRejectedWithoutCallingTheLlm() {
        ModerationService.ModerationOutcome outcome = service.moderate("إلى سامي، اتصل بنا ٠٧١٢٣٤٥٦٧");
        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.llmCall()).isNull();
    }

    @Test
    void linksAreRejected() {
        assertThat(service.moderate("see www.example.com").allowed()).isFalse();
    }

    @Test
    void tooLongIsRejected() {
        assertThat(service.moderate("ح".repeat(301)).allowed()).isFalse();
    }

    @Test
    void ordinaryTextGoesToTheLlm() {
        llm.enqueue(new ModerationResponse(true, null));
        ModerationService.ModerationOutcome outcome = service.moderate("إلى سامي الحبيب، نحبك كثيرًا");
        assertThat(outcome.allowed()).isTrue();
        assertThat(outcome.llmCall()).isNotNull();
    }

    @Test
    void llmRefusalIsPassedThrough() {
        llm.enqueue(new ModerationResponse(false, "Contains an insult."));
        ModerationService.ModerationOutcome outcome = service.moderate("نص");
        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.reason()).isEqualTo("Contains an insult.");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=ModerationServiceTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`ModerationResponse.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ModerationResponse(
        boolean allowed,
        @JsonPropertyDescription("If not allowed: one short English sentence the parent can act on; otherwise null") String reason
) {
}
```

`ModerationService.java`:
```java
package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmRequest;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ModerationService {

    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("[0-9٠-٩]{7,}");

    private final LlmGateway llm;
    private final PromptLibrary prompts;
    private final StorybookProperties properties;

    public record ModerationOutcome(boolean allowed, String reason, LlmCall<ModerationResponse> llmCall) {
    }

    public ModerationOutcome moderate(String text) {
        if (text == null || text.isBlank()) {
            return new ModerationOutcome(true, null, null);
        }
        int max = properties.getLimits().getDedicationMaxChars();
        if (text.length() > max) {
            return new ModerationOutcome(false, "The dedication is longer than " + max + " characters.", null);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String compact = text.replaceAll("[\\s\\-]", "");
        if (lower.contains("http") || lower.contains("www.") || text.contains("@") || LONG_DIGIT_RUN.matcher(compact).find()) {
            return new ModerationOutcome(false, "Links, email addresses and phone numbers cannot be printed.", null);
        }
        LlmCall<ModerationResponse> call = llm.call(LlmRequest.of(LlmPurpose.MODERATION,
                prompts.get("moderation-system"), "Dedication:\n" + text, ModerationResponse.class));
        return new ModerationOutcome(call.value().allowed(), call.value().reason(), call);
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=ModerationServiceTest`
Expected: 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/story src/test/java/com/doova/ktab/features/storybook/story
git commit -m "feat(storybook): add dedication moderation"
```

---

### Task 12: Character and scene prompts, image downscaler, `VisualQa`

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/character/CharacterPrompts.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/image/ImageDownscaler.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/illustration/VisualQaResponse.java`, `VisualQa.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/character/CharacterPromptsTest.java`, `src/test/java/com/doova/ktab/features/storybook/image/ImageDownscalerTest.java`, `src/test/java/com/doova/ktab/features/storybook/illustration/VisualQaTest.java`

**Interfaces:**
- Consumes: `ChildAppearance`, `CompanionSpec` (Task 8), `PagePlan` (Task 8), `ChildGender`, `AgeBand` (Task 2), `LlmGateway` (Task 4), `ReferenceImage` (Task 5).
- Produces:
  - `CharacterPrompts.sheet(ChildGender g, AgeBand band, ChildAppearance a)`, `CharacterPrompts.sheetFromPhoto(ChildGender g, AgeBand band)`, `CharacterPrompts.companionSheet(CompanionSpec c)`, `CharacterPrompts.scene(String sceneEn, TextZone zone, boolean hasCompanion, boolean hijab)`, `CharacterPrompts.cover(String coverSceneEn, boolean hasCompanion, boolean hijab)` — all static, return English prompts.
  - `ImageDownscaler.toJpeg(byte[] image, int maxSidePx) : byte[]` — never upscales; always JPEG quality 0.85.
  - `record VisualQaResponse(boolean identityMatch, boolean strayText, boolean anatomyOk, boolean safeForChildren, List<String> problems)` with `boolean passed()`.
  - `VisualQa.check(byte[] candidate, List<ReferenceImage> references, String sceneEn) : LlmCall<VisualQaResponse>` — downsizes every image to `qaMaxSidePx` before sending; references first, candidate last.

- [ ] **Step 1: Write the failing tests**

`CharacterPromptsTest.java`:
```java
package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterPromptsTest {

    @Test
    void sheetDescribesTheChildAndBothViews() {
        String p = CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE);
        assertThat(p).contains("girl").contains("olive skin").contains("black short curly hair")
                .contains("front view").contains("three-quarter view").contains("No text");
    }

    @Test
    void hijabReplacesHairInTheSheet() {
        ChildAppearance withHijab = new ChildAppearance(ChildAppearance.SkinTone.LIGHT, null, null,
                ChildAppearance.EyeColor.GREEN, true, true);
        String p = CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_9_10, withHijab);
        assertThat(p).contains("hijab").contains("glasses").doesNotContain("null");
    }

    @Test
    void sceneReservesTheTextZoneAndForbidsText() {
        String p = CharacterPrompts.scene("The CHILD kicks a ball in a park.", TextZone.BOTTOM, false, false);
        assertThat(p).contains("bottom third").contains("kicks a ball").contains("no text");
        assertThat(p).doesNotContain("COMPANION");
    }

    @Test
    void sceneMentionsTheCompanionReferenceWhenPresent() {
        assertThat(CharacterPrompts.scene("The CHILD and COMPANION run.", TextZone.TOP, true, false))
                .contains("companion sheet");
    }

    @Test
    void hijabIsRestatedInEveryScene() {
        assertThat(CharacterPrompts.scene("The CHILD reads.", TextZone.TOP, false, true)).contains("hijab");
    }
}
```

`ImageDownscalerTest.java`:
```java
package com.doova.ktab.features.storybook.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageDownscalerTest {

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void shrinksA2kPngTo1024Jpeg() throws Exception {
        byte[] jpeg = ImageDownscaler.toJpeg(png(2048, 2048), 1024);
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(result.getWidth()).isEqualTo(1024);
        assertThat(result.getHeight()).isEqualTo(1024);
        assertThat(jpeg[0]).isEqualTo((byte) 0xFF);
        assertThat(jpeg[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void neverUpscales() throws Exception {
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(ImageDownscaler.toJpeg(png(300, 200), 1024)));
        assertThat(result.getWidth()).isEqualTo(300);
        assertThat(result.getHeight()).isEqualTo(200);
    }
}
```

`VisualQaTest.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.image.ReferenceImage;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisualQaTest {

    private static byte[] png(int size) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void sendsReferencesFirstAndCandidateLastAsSmallJpegs() throws Exception {
        FakeLlmGateway llm = new FakeLlmGateway();
        llm.enqueue(new VisualQaResponse(true, false, true, true, List.of()));
        VisualQa qa = new VisualQa(llm, new PromptLibrary(), new StorybookProperties());

        var call = qa.check(png(2048), List.of(new ReferenceImage(png(2048), "image/png"),
                new ReferenceImage(png(1024), "image/png")), "The CHILD waves.");

        assertThat(call.value().passed()).isTrue();
        var request = llm.requests().get(0);
        assertThat(request.purpose()).isEqualTo(LlmPurpose.VISUAL_QA);
        assertThat(request.images()).hasSize(3).allSatisfy(i -> assertThat(i.mediaType()).isEqualTo("image/jpeg"));
        assertThat(request.user()).contains("The CHILD waves.").contains("Image 3 is the illustration to check");
    }

    @Test
    void anyFailedCheckFailsThePage() {
        assertThat(new VisualQaResponse(true, true, true, true, List.of("letters on a sign")).passed()).isFalse();
        assertThat(new VisualQaResponse(false, false, true, true, List.of()).passed()).isFalse();
        assertThat(new VisualQaResponse(true, false, false, true, List.of()).passed()).isFalse();
        assertThat(new VisualQaResponse(true, false, true, false, List.of()).passed()).isFalse();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='CharacterPromptsTest,ImageDownscalerTest,VisualQaTest'`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement `CharacterPrompts`**

```java
package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.TextZone;

public final class CharacterPrompts {

    private static final String NO_TEXT = "Absolutely no text, letters, numbers, signs, logos or writing anywhere in the picture.";
    private static final String STYLE = "Match the art style of the style reference image exactly: its line work, colour palette, texture and lighting.";
    private static final String MODEST = "Modest, simple, bright everyday children's clothes with long sleeves.";

    private CharacterPrompts() {
    }

    public static String sheet(ChildGender gender, AgeBand band, ChildAppearance appearance) {
        return "Character reference sheet for a children's picture book. One " + gender.en() + " aged about "
                + mid(band) + ", shown twice side by side on a plain white background: front view on the left, "
                + "three-quarter view on the right, full body, standing, gentle smile, identical outfit in both views. "
                + "Appearance: " + appearance.describeEn() + ". " + MODEST + " " + STYLE + " No text, labels or captions. "
                + NO_TEXT;
    }

    public static String sheetFromPhoto(ChildGender gender, AgeBand band) {
        return "Create a stylized children's picture-book character of the child in the photo reference, keeping "
                + "their recognizable features: face shape, skin tone, hair colour and style, eye colour, glasses and "
                + "headwear if any. A " + gender.en() + " aged about " + mid(band) + ". Show the character twice side "
                + "by side on a plain white background: front view on the left, three-quarter view on the right, full "
                + "body, standing, gentle smile, identical outfit in both views. " + MODEST + " " + STYLE + " " + NO_TEXT;
    }

    public static String companionSheet(CompanionSpec companion) {
        return "Character reference sheet for a children's picture book: " + companion.describeEn()
                + ", shown twice side by side on a plain white background: front view on the left, three-quarter view "
                + "on the right, friendly expression. " + STYLE + " " + NO_TEXT;
    }

    public static String scene(String sceneEn, TextZone zone, boolean hasCompanion, boolean hijab) {
        return "Illustrate one square page of a children's picture book. " + STYLE + " "
                + "The CHILD is exactly the character in the character sheet: same face, skin tone, hair, eyes, glasses "
                + "and clothes" + (hijab ? ", always wearing the same hijab" : "") + ". "
                + (hasCompanion ? "The COMPANION is exactly the character in the companion sheet. " : "")
                + "Scene: " + sceneEn + " "
                + "Composition: the " + zone.name().toLowerCase() + " third of the picture is calm and empty "
                + "(plain sky, wall, grass, water or floor) with no important detail, reserved for text. " + NO_TEXT;
    }

    public static String cover(String coverSceneEn, boolean hasCompanion, boolean hijab) {
        return scene(coverSceneEn + " This is the book cover: warm, inviting, the CHILD clearly visible.",
                TextZone.TOP, hasCompanion, hijab);
    }

    private static String mid(AgeBand band) {
        return String.valueOf((band.minAge() + band.maxAge()) / 2);
    }
}
```

- [ ] **Step 4: Implement `ImageDownscaler`**

```java
package com.doova.ktab.features.storybook.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class ImageDownscaler {

    private ImageDownscaler() {
    }

    public static byte[] toJpeg(byte[] image, int maxSidePx) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(image));
            if (source == null) {
                throw new IllegalArgumentException("Not a readable image");
            }
            double scale = Math.min(1.0, (double) maxSidePx / Math.max(source.getWidth(), source.getHeight()));
            int w = (int) Math.round(source.getWidth() * scale);
            int h = (int) Math.round(source.getHeight() * scale);

            BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(source, 0, 0, w, h, null);
            g.dispose();

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 5: Implement `VisualQaResponse` and `VisualQa`**

`VisualQaResponse.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record VisualQaResponse(
        @JsonPropertyDescription("The CHILD clearly matches the character sheet") boolean identityMatch,
        @JsonPropertyDescription("Any letters, words, numbers, logos or writing-like marks appear") boolean strayText,
        @JsonPropertyDescription("No extra or missing fingers, limbs or eyes, no distorted faces or bodies") boolean anatomyOk,
        @JsonPropertyDescription("Nothing frightening, violent, immodest or unsuitable for a young child") boolean safeForChildren,
        @JsonPropertyDescription("One short sentence per issue found; empty if none") List<String> problems
) {
    public boolean passed() {
        return identityMatch && !strayText && anatomyOk && safeForChildren;
    }
}
```

`VisualQa.java`:
```java
package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.image.ImageDownscaler;
import com.doova.ktab.features.storybook.image.ReferenceImage;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmImage;
import com.doova.ktab.features.storybook.llm.LlmRequest;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VisualQa {

    private final LlmGateway llm;
    private final PromptLibrary prompts;
    private final StorybookProperties properties;

    /**
     * @param references in order: CHILD sheet, style reference, then COMPANION sheet if any
     */
    public LlmCall<VisualQaResponse> check(byte[] candidate, List<ReferenceImage> references, String sceneEn) {
        int maxSide = properties.getImage().getQaMaxSidePx();
        List<LlmImage> images = new ArrayList<>();
        for (ReferenceImage reference : references) {
            images.add(new LlmImage(ImageDownscaler.toJpeg(reference.bytes(), maxSide), "image/jpeg"));
        }
        images.add(new LlmImage(ImageDownscaler.toJpeg(candidate, maxSide), "image/jpeg"));

        String user = "Images 1 to " + references.size() + " are references: image 1 is the CHILD's character sheet"
                + (references.size() > 1 ? ", image 2 is the style reference" : "")
                + (references.size() > 2 ? ", image 3 is the COMPANION's sheet" : "")
                + ". Image " + images.size() + " is the illustration to check.\n"
                + "The scene it should show: " + sceneEn;

        return llm.call(LlmRequest.of(LlmPurpose.VISUAL_QA, prompts.get("visual-qa-system"), user, VisualQaResponse.class)
                .withImages(images));
    }
}
```

In `VisualQaTest`, the user text for 2 references reads "Image 3 is the illustration to check" — matching `images.size()` = 3.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='CharacterPromptsTest,ImageDownscalerTest,VisualQaTest'`
Expected: 9 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/character src/main/java/com/doova/ktab/features/storybook/image src/main/java/com/doova/ktab/features/storybook/illustration src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): add character/scene prompts, image downscaler and visual QA"
```

---

### Task 13: Phase 0 spike — character consistency harness

**Files:**
- Create: `src/test/java/com/doova/ktab/features/storybook/spike/SpikeClients.java`, `CharacterConsistencySpikeIT.java`
- Create: `src/test/resources/storybook/spike/characters.json`, `scenes.json`

**Interfaces:**
- Consumes: `GeminiImageProvider`, `ImageRequest`, `ReferenceImage` (Task 5); `AnthropicLlmGateway` (Task 4); `VisualQa` (Task 12); `CharacterPrompts`, `ChildAppearance` (Tasks 8, 12); `CostCalculator` (Task 3); `PromptLibrary` (Task 6); `StorybookProperties` (Task 2); the style reference `src/main/resources/storybook/styles/soft_watercolor.png` (art-director prerequisite).
- Produces: for each model × character, files under `target/storybook-spike/consistency/<model>/<characterId>/` (`sheet.png`, `page-01-try-1.png` …), plus `target/storybook-spike/consistency/results.csv`, `human-scores.csv` (blank template) and `index.html` (contact sheet). Printed summary per model: first-attempt QA pass rate, pass rate after up to 3 retries, mean cost per 15-page book.

Budget: 2 models × 5 characters × (1 sheet + 15 pages + retries) ≈ 200 images and ≈ 200 QA calls — roughly $30. The test never runs in CI: it needs `STORYBOOK_SPIKE=true`.

- [ ] **Step 1: Write the fixtures**

`src/test/resources/storybook/spike/characters.json` — five deliberately varied children:
```json
[
  {"id": "c1-boy-curly", "gender": "BOY", "ageBand": "AGE_6_8",
   "appearance": {"skinTone": "OLIVE", "hairColor": "BLACK", "hairStyle": "SHORT_CURLY", "eyeColor": "BROWN", "hijab": false, "glasses": false}},
  {"id": "c2-girl-hijab", "gender": "GIRL", "ageBand": "AGE_9_10",
   "appearance": {"skinTone": "LIGHT", "eyeColor": "GREEN", "hijab": true, "glasses": true}},
  {"id": "c3-girl-braids", "gender": "GIRL", "ageBand": "AGE_3_5",
   "appearance": {"skinTone": "DARK_BROWN", "hairColor": "BLACK", "hairStyle": "BRAIDS", "eyeColor": "DARK_BROWN", "hijab": false, "glasses": false}},
  {"id": "c4-boy-glasses", "gender": "BOY", "ageBand": "AGE_9_10",
   "appearance": {"skinTone": "TAN", "hairColor": "DARK_BROWN", "hairStyle": "VERY_SHORT", "eyeColor": "HAZEL", "hijab": false, "glasses": true}},
  {"id": "c5-girl-red", "gender": "GIRL", "ageBand": "AGE_6_8",
   "appearance": {"skinTone": "VERY_LIGHT", "hairColor": "RED", "hairStyle": "LONG_STRAIGHT", "eyeColor": "BLUE", "hijab": false, "glasses": false}}
]
```

`src/test/resources/storybook/spike/scenes.json` — 15 scenes that stress consistency (distance, angle, lighting, action):
```json
[
  {"zone": "TOP",    "scene": "The CHILD wakes up in bed, stretching and smiling, morning light through the window."},
  {"zone": "BOTTOM", "scene": "Close-up of the CHILD's face, laughing, eyes closed with joy."},
  {"zone": "TOP",    "scene": "The CHILD runs across a green park, seen from far away, small in the frame."},
  {"zone": "TOP",    "scene": "The CHILD, seen from behind and slightly to the side, looks at the sea at sunset."},
  {"zone": "BOTTOM", "scene": "The CHILD sits cross-legged on a rug, building a tower of wooden blocks."},
  {"zone": "TOP",    "scene": "The CHILD kicks a football, mid-motion, in a sunny courtyard."},
  {"zone": "TOP",    "scene": "The CHILD climbs a small tree in an orchard, reaching for an orange."},
  {"zone": "BOTTOM", "scene": "The CHILD eats breakfast at a kitchen table with bread, olives and tea."},
  {"zone": "TOP",    "scene": "The CHILD waves goodbye from a school gate, in soft rain with an umbrella."},
  {"zone": "BOTTOM", "scene": "The CHILD lies on the grass at night, looking up at the stars."},
  {"zone": "TOP",    "scene": "The CHILD paints a picture at an easel, colourful paint on the hands."},
  {"zone": "BOTTOM", "scene": "The CHILD rides a bicycle down a quiet street lined with palm trees."},
  {"zone": "TOP",    "scene": "The CHILD, surprised, opens a gift box in a living room."},
  {"zone": "TOP",    "scene": "The CHILD swims in a calm pool wearing modest swimwear, splashing happily."},
  {"zone": "BOTTOM", "scene": "The CHILD sleeps in bed hugging a teddy bear, a night light glowing."}
]
```

- [ ] **Step 2: Write `SpikeClients`**

```java
package com.doova.ktab.features.storybook.spike;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.image.GeminiImageProvider;
import com.doova.ktab.features.storybook.llm.AnthropicLlmGateway;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;

import java.io.IOException;

/** Builds real clients without starting Spring (no DB needed for the spike). */
final class SpikeClients {

    private SpikeClients() {
    }

    static GeminiImageProvider imageProvider(StorybookProperties properties) throws IOException {
        Client client = Client.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location("global")
                .vertexAI(true)
                .credentials(GoogleCredentials.getApplicationDefault())
                .build();
        return new GeminiImageProvider(client, properties);
    }

    static AnthropicLlmGateway llm(StorybookProperties properties) {
        return new AnthropicLlmGateway(AnthropicOkHttpClient.fromEnv(), properties);
    }
}
```

- [ ] **Step 3: Write the harness**

```java
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
```

- [ ] **Step 4: Verify it is skipped by default**

Run: `./mvnw -q test -Dtest=CharacterConsistencySpikeIT`
Expected: 0 tests run (skipped). No API calls.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/doova/ktab/features/storybook/spike src/test/resources/storybook/spike
git commit -m "test(storybook): add Phase 0 character consistency spike harness"
```

---

### Task 14: Phase 0 spike — Arabic quality harness

**Files:**
- Create: `src/test/java/com/doova/ktab/features/storybook/spike/ArabicQualitySpikeIT.java`
- Create: `src/test/resources/storybook/spike/arabic-matrix.json`
- Create: `docs/storybook/arabic-grading-guide.md`

**Interfaces:**
- Consumes: `StoryWriter` (Task 8), `StoryCritic` (Task 10), `TashkeelFilter` (Task 9), `BlueprintCatalog` (Task 7), `SpikeClients` (Task 13).
- Produces: `target/storybook-spike/arabic/story-XX.md` (one per story: settings, title, each page's final text, critic findings before and after one rewrite round) and `target/storybook-spike/arabic/grading.csv` (a row per page for the native editor).

The 20-story matrix covers both genders, all three age bands and all four varieties (MSA ×8, each dialect ×4), matching the spec's "a native editor grades the LLM's Arabic and tashkeel on 20 stories". Budget: about $2.

- [ ] **Step 1: Write the matrix**

`src/test/resources/storybook/spike/arabic-matrix.json`:
```json
[
  {"name": "سامي", "gender": "BOY",  "ageBand": "AGE_3_5",  "variety": "MSA", "pageCount": 10},
  {"name": "لَيْلى", "gender": "GIRL", "ageBand": "AGE_3_5",  "variety": "MSA", "pageCount": 10},
  {"name": "يوسف", "gender": "BOY",  "ageBand": "AGE_6_8",  "variety": "MSA", "pageCount": 12},
  {"name": "مريم", "gender": "GIRL", "ageBand": "AGE_6_8",  "variety": "MSA", "pageCount": 12},
  {"name": "عُمَر", "gender": "BOY",  "ageBand": "AGE_9_10", "variety": "MSA", "pageCount": 15},
  {"name": "فاطمة", "gender": "GIRL", "ageBand": "AGE_9_10", "variety": "MSA", "pageCount": 15},
  {"name": "آدم",  "gender": "BOY",  "ageBand": "AGE_6_8",  "variety": "MSA", "pageCount": 15},
  {"name": "سارة", "gender": "GIRL", "ageBand": "AGE_6_8",  "variety": "MSA", "pageCount": 10},
  {"name": "كريم", "gender": "BOY",  "ageBand": "AGE_3_5",  "variety": "LEBANESE", "pageCount": 10},
  {"name": "ريم",  "gender": "GIRL", "ageBand": "AGE_6_8",  "variety": "LEBANESE", "pageCount": 12},
  {"name": "جاد",  "gender": "BOY",  "ageBand": "AGE_9_10", "variety": "LEBANESE", "pageCount": 15},
  {"name": "نور",  "gender": "GIRL", "ageBand": "AGE_6_8",  "variety": "LEBANESE", "pageCount": 10},
  {"name": "مصطفى", "gender": "BOY", "ageBand": "AGE_3_5",  "variety": "EGYPTIAN", "pageCount": 10},
  {"name": "هنا",  "gender": "GIRL", "ageBand": "AGE_6_8",  "variety": "EGYPTIAN", "pageCount": 12},
  {"name": "علي",  "gender": "BOY",  "ageBand": "AGE_9_10", "variety": "EGYPTIAN", "pageCount": 15},
  {"name": "ملك",  "gender": "GIRL", "ageBand": "AGE_6_8",  "variety": "EGYPTIAN", "pageCount": 10},
  {"name": "فيصل", "gender": "BOY",  "ageBand": "AGE_3_5",  "variety": "GULF", "pageCount": 10},
  {"name": "جود",  "gender": "GIRL", "ageBand": "AGE_6_8",  "variety": "GULF", "pageCount": 12},
  {"name": "سلطان", "gender": "BOY", "ageBand": "AGE_9_10", "variety": "GULF", "pageCount": 15},
  {"name": "شهد",  "gender": "GIRL", "ageBand": "AGE_6_8",  "variety": "GULF", "pageCount": 10}
]
```

The sample blueprint only covers ages 3–8. For the three `AGE_9_10` rows, the harness still uses it (the spike measures language quality, not blueprint fit); note that in the results.

- [ ] **Step 2: Write the harness**

```java
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
```

- [ ] **Step 3: Write the grading guide for the native editors**

`docs/storybook/arabic-grading-guide.md`:
```markdown
# Phase 0 — Arabic grading guide

Grade every page in `grading.csv` (one row per page). Open `story-XX.md` to read the page in context.

| Column | Scale | 5 means | 1 means |
|---|---|---|---|
| grammar_1to5 | 1–5 | Flawless, natural Arabic | Several errors or unclear sentences |
| gender_agreement_error_y_n | y / n | — | Any verb, adjective or pronoun about the child has the wrong gender |
| tashkeel_1to5_msa_only | 1–5 (MSA rows only) | Every mark correct, including case endings | Frequent wrong or missing marks |
| dialect_fidelity_1to5_dialect_only | 1–5 (dialect rows only) | Follows the style guide exactly; sounds native | Mixes MSA, breaks guide rules, or has tashkeel |
| age_fit_1to5 | 1–5 | Vocabulary and length fit the age band | Far too hard or too babyish |

Also note in `notes`: any word that should be removed from children's books, and any rule the dialect guide is missing.

Exit criterion (decision D11 in the overview): average grade ≥ 4 on every 1–5 column, and zero gender-agreement errors in MSA stories.

The MSA editor also decides the `PARTIAL` tashkeel rule (decision D3): today it keeps shadda and tanween only.
```

- [ ] **Step 4: Verify it is skipped by default**

Run: `./mvnw -q test -Dtest=ArabicQualitySpikeIT`
Expected: 0 tests run (skipped).

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/doova/ktab/features/storybook/spike/ArabicQualitySpikeIT.java src/test/resources/storybook/spike/arabic-matrix.json docs/storybook/arabic-grading-guide.md
git commit -m "test(storybook): add Phase 0 Arabic quality spike harness and grading guide"
```

---

### Task 15: Run Phase 0 and record the go/no-go decision

**Files:**
- Create: `docs/storybook/phase-0-results.md`

**Interfaces:**
- Consumes: outputs of Tasks 13 and 14.
- Produces: the decision record the rest of the MVP plans depend on.

- [ ] **Step 1: Run the consistency spike**

Run: `STORYBOOK_SPIKE=true GCP_PROJECT_ID=<project> ANTHROPIC_API_KEY=<key> ./mvnw test -Dtest=CharacterConsistencySpikeIT`
Expected: two `SPIKE <model>: …` lines printed; `target/storybook-spike/consistency/index.html` opens as a contact sheet.

- [ ] **Step 2: Run the Arabic spike**

Run: `STORYBOOK_SPIKE=true ANTHROPIC_API_KEY=<key> ./mvnw test -Dtest=ArabicQualitySpikeIT`
Expected: `ARABIC SPIKE: 20 stories …` printed; 20 `story-XX.md` files and `grading.csv`.

- [ ] **Step 3: Collect human scores**

Send `human-scores.csv` + `index.html` to two reviewers (consistency and style stability, per model × character) and `grading.csv` + the story files to the native editors with `docs/storybook/arabic-grading-guide.md`.

- [ ] **Step 4: Write the results**

`docs/storybook/phase-0-results.md`:
```markdown
# Phase 0 results

Date run: YYYY-MM-DD · Ktab commit: <sha>

## Character consistency (decision D11 thresholds in brackets)

| Model | First-try QA pass [≥ 85%] | Pass after ≤ 3 retries [≥ 97%] | Human consistency avg [≥ 4] | Style stability avg | Mean cost per 15-page book [≤ $3.50] |
|---|---|---|---|---|---|
| gemini-3.1-flash-image | | | | | |
| gemini-3-pro-image | | | | | |

Most common QA failures (from `results.csv` → `problems`):

## Arabic quality

| Variety | Stories | Grammar avg [≥ 4] | Gender errors [0 for MSA] | Tashkeel avg [≥ 4] | Dialect fidelity avg [≥ 4] | Age fit avg [≥ 4] |
|---|---|---|---|---|---|---|
| MSA | 8 | | | | — | |
| Lebanese | 4 | | | — | | |
| Egyptian | 4 | | | — | | |
| Gulf | 4 | | | — | | |

Critic accuracy: pages the critic failed that the editor graded ≥ 4 (false alarms): __; pages the critic passed that the editor graded ≤ 2 (misses): __.

## Decisions

- Image model: go / no-go with Nano Banana 2 as primary. If no-go: build FLUX.2 Pro and GPT Image 2.5 adapters (decision D10) and re-run.
- Word limits per age band (D2): keep / change to __.
- `PARTIAL` tashkeel rule (D3): keep shadda + tanween / change to __.
- Prompt changes made during the spike (link commits):
```

Fill every blank from the spike outputs and reviewer sheets.

- [ ] **Step 5: Commit**

```bash
git add docs/storybook/phase-0-results.md
git commit -m "docs(storybook): record Phase 0 spike results and go/no-go decision"
```

**Gate:** do not start sub-plan 02 until `phase-0-results.md` records a "go".
