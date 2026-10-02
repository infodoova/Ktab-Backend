package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.story.BlueprintPageBeat;
import com.doova.ktab.features.storybook.story.CharacterBibleResponse;
import com.doova.ktab.features.storybook.story.CharacterVisualSpec;
import com.doova.ktab.features.storybook.story.CriticResponse;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.PageVerdict;
import com.doova.ktab.features.storybook.story.StoryBlueprintResponse;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * An answer the pipeline cannot use must never be stored or passed on: it is checked once, centrally, for every call,
 * retried a couple of times, and then reported as a retryable failure naming the call.
 */
class LlmResponseValidationTest {

    // ---- what counts as unusable -------------------------------------------------------------------------------

    @Test
    void anEmptyBlueprintIsUnusable() {
        assertThat(new StoryBlueprintResponse(null, null, null).problems()).isNotEmpty();
        assertThat(new StoryBlueprintResponse("t", "p", List.of()).problems()).isNotEmpty();
        assertThat(new StoryBlueprintResponse(null, null, List.of(new BlueprintPageBeat(1, "b", "e", "s", List.of()))).problems()).isEmpty();
        assertThat(new StoryBlueprintResponse(null, null, List.of(new BlueprintPageBeat(1, " ", "e", "s", List.of()))).problems()).isNotEmpty();
    }

    @Test
    void aBibleWithoutCharactersIsUnusable() {
        assertThat(new CharacterBibleResponse(null, null, null).problems()).isNotEmpty();
        assertThat(new CharacterBibleResponse(List.of(), "v", "s").problems()).isNotEmpty();
        assertThat(new CharacterBibleResponse(List.of(new CharacterVisualSpec("ليلى", "r", "l", "c", "p")), null, null).problems()).isEmpty();
        assertThat(new CharacterBibleResponse(List.of(new CharacterVisualSpec(" ", "r", "l", "c", "p")), null, null).problems()).isNotEmpty();
    }

    @Test
    void aPlanWithoutPagesIsUnusable() {
        assertThat(new StoryPlanResponse("t", "c", null).problems()).isNotEmpty();
        assertThat(new StoryPlanResponse("t", "c", List.of()).problems()).isNotEmpty();
        PagePlan page = new PagePlan(1, "نص", "scene", List.of(), TextZone.TOP);
        assertThat(new StoryPlanResponse("t", "c", List.of(page)).problems()).isEmpty();
    }

    @Test
    void aPageWithoutTextIsUnusable() {
        assertThat(new PagePlan(1, null, "scene", List.of(), TextZone.TOP).problems()).isNotEmpty();
        assertThat(new PagePlan(1, "  ", "scene", List.of(), TextZone.TOP).problems()).isNotEmpty();
        assertThat(new PagePlan(1, "نص", "scene", List.of(), TextZone.TOP).problems()).isEmpty();
    }

    @Test
    void aCriticAnswerWithoutVerdictsIsUnusable() {
        assertThat(new CriticResponse(null).problems()).isNotEmpty();
        assertThat(new CriticResponse(List.of()).problems()).isNotEmpty();
        assertThat(new CriticResponse(List.of(new PageVerdict(1, true, List.of()))).problems()).isEmpty();
    }

    // ---- the central check, in front of both providers -----------------------------------------------------------

    private final StorybookProperties properties = new StorybookProperties();
    private final LlmGateway openAi = mock(LlmGateway.class);
    private final LlmGateway anthropic = mock(LlmGateway.class);
    private final StorybookLlmRouter router = new StorybookLlmRouter(properties, openAi, anthropic);

    private static LlmRequest<StoryBlueprintResponse> request() {
        return LlmRequest.of(LlmPurpose.STORY_BLUEPRINT, "system", "user", StoryBlueprintResponse.class);
    }

    private static LlmCall<StoryBlueprintResponse> call(StoryBlueprintResponse value) {
        return new LlmCall<>(value, "m", 10, 10, 5);
    }

    @Test
    void anUnusableAnswerIsAskedForAgainAndTheGoodOneIsReturned() {
        StoryBlueprintResponse good = new StoryBlueprintResponse("t", "p", List.of(new BlueprintPageBeat(1, "b", "e", "s", List.of())));
        doReturn(call(new StoryBlueprintResponse(null, null, null))).doReturn(call(good)).when(openAi).call(any());

        LlmCall<StoryBlueprintResponse> result = router.call(request());

        assertThat(result.value()).isSameAs(good);
        verify(openAi, times(2)).call(any());
    }

    @Test
    void anAnswerThatStaysUnusableBecomesARetryableFailureNamingTheCall() {
        doReturn(call(new StoryBlueprintResponse(null, null, null))).when(openAi).call(any());

        assertThatThrownBy(() -> router.call(request()))
                .isInstanceOfSatisfying(LlmCallFailedException.class, e -> {
                    assertThat(e.retryable()).isTrue();
                    assertThat(e.getMessage()).contains("STORY_BLUEPRINT");
                });
        verify(openAi, times(3)).call(any());
    }

    @Test
    void theSameCheckGuardsTheAnthropicProvider() {
        properties.getLlm().setProvider("ANTHROPIC");
        doReturn(call(new StoryBlueprintResponse(null, null, List.of()))).when(anthropic).call(any());

        assertThatThrownBy(() -> router.call(request())).isInstanceOf(LlmCallFailedException.class);
    }

    @Test
    void plainTextAnswersAreNotChecked() {
        doReturn(new LlmCall<>("", "m", 1, 1, 1)).when(openAi).call(any());

        LlmCall<String> result = router.call(LlmRequest.of(LlmPurpose.STORY_PLAN, "s", "u", String.class));

        assertThat(result.value()).isEmpty();
        verify(openAi, times(1)).call(any());
    }
}
