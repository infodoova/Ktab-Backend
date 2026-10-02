package com.doova.ktab.features.storybook.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiLlmGatewayTest {

    @Test
    void extractJson_pureJson_returnsExactString() {
        String json = "{\"titleAr\": \"مغامرة سارة\"}";
        assertThat(OpenAiLlmGateway.extractJson(json)).isEqualTo(json);
    }

    @Test
    void extractJson_markdownFencedJson_stripsFences() {
        String raw = "```json\n{\"titleAr\": \"مغامرة سارة\"}\n```";
        assertThat(OpenAiLlmGateway.extractJson(raw)).isEqualTo("{\"titleAr\": \"مغامرة سارة\"}");
    }

    @Test
    void extractJson_textWithSurroundingChatter_extractsOnlyBraces() {
        String raw = "Here is your requested response:\n{\"titleAr\": \"مغامرة سارة\"}\nHope you like it!";
        assertThat(OpenAiLlmGateway.extractJson(raw)).isEqualTo("{\"titleAr\": \"مغامرة سارة\"}");
    }

    @Test
    void extractJson_jsonArray_extractsBrackets() {
        String raw = "Output: [{\"name\": \"سارة\"}]";
        assertThat(OpenAiLlmGateway.extractJson(raw)).isEqualTo("[{\"name\": \"سارة\"}]");
    }

    @Test
    void extractJson_nullOrEmpty_returnsEmptyObject() {
        assertThat(OpenAiLlmGateway.extractJson(null)).isEqualTo("{}");
        assertThat(OpenAiLlmGateway.extractJson("   ")).isEqualTo("{}");
    }

    @Test
    void extractJson_nestedObjectsAndArrays_extractsOuterObject() {
        String raw = "Prefacing thoughts... {\"characters\": [{\"name\": \"سامي\", \"items\": [1, 2]}], \"valid\": true} trailing notes";
        assertThat(OpenAiLlmGateway.extractJson(raw))
                .isEqualTo("{\"characters\": [{\"name\": \"سامي\", \"items\": [1, 2]}], \"valid\": true}");
    }

    @Test
    void extractJson_multipleCodeFences_extractsFirstJsonContent() {
        String raw = "```json\n{\"title\": \"كتاب\"}\n```\nSome commentary here\n```bash\necho hello\n```";
        assertThat(OpenAiLlmGateway.extractJson(raw)).isEqualTo("{\"title\": \"كتاب\"}");
    }

    @Test
    void extractJson_escapedQuotesInsideString_preservesEscapes() {
        String raw = "{\"quote\": \"قال سامي: \\\"أهلاً!\\\"\"}";
        assertThat(OpenAiLlmGateway.extractJson(raw)).isEqualTo(raw);
    }

    @Test
    void aSinglePageRewriteWrappedInAPagesArrayIsUnwrapped() throws Exception {
        String raw = "{\"pages\":[{\"scene\":\"A boy on the grass\",\"text\":\"جلس سامي على العشب.\"}]}";

        String json = OpenAiLlmGateway.unwrapSingleItem(raw, com.doova.ktab.features.storybook.story.PagePlan.class);

        com.doova.ktab.features.storybook.story.PagePlan page = new ObjectMapper()
                .readValue(json, com.doova.ktab.features.storybook.story.PagePlan.class);
        assertThat(page.textAr()).isEqualTo("جلس سامي على العشب.");
        assertThat(page.sceneEn()).isEqualTo("A boy on the grass");
    }

    @Test
    void aWholePlanKeepsItsPagesArray() {
        String raw = "{\"titleAr\":\"t\",\"pages\":[{\"pageNumber\":1}]}";

        assertThat(OpenAiLlmGateway.unwrapSingleItem(raw, com.doova.ktab.features.storybook.story.StoryPlanResponse.class)).isEqualTo(raw);
    }

    @Test
    void aPlainPageIsLeftAlone() {
        String raw = "{\"pageNumber\":1,\"textAr\":\"نص\"}";

        assertThat(OpenAiLlmGateway.unwrapSingleItem(raw, com.doova.ktab.features.storybook.story.PagePlan.class)).isEqualTo(raw);
    }

    @Test
    void anAccountThatIsOutOfCreditsIsNotRetried() {
        Exception outOfCredits = new RuntimeException("429 - {\"error\":{\"message\":\"You have no credits remaining.\","
                + "\"type\":\"insufficient_quota\",\"code\":\"credit_balance_exhausted\"}}");

        assertThat(OpenAiLlmGateway.isRetryable(outOfCredits)).isFalse();
        assertThat(OpenAiLlmGateway.isOutOfCredits(outOfCredits)).isTrue();
    }

    @Test
    void anOrdinaryRateLimitIsStillRetried() {
        Exception rateLimit = new RuntimeException("429 - Rate limit reached for gpt-6-luna on tokens per min (TPM)");

        assertThat(OpenAiLlmGateway.isRetryable(rateLimit)).isTrue();
        assertThat(OpenAiLlmGateway.isOutOfCredits(rateLimit)).isFalse();
    }
}
