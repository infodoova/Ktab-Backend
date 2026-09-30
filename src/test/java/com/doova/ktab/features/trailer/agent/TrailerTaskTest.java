package com.doova.ktab.features.trailer.agent;

import com.doova.ktab.features.trailer.config.TrailerVoice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrailerTaskTest {

    @Test
    void taskCarriesBookFactsVoiceAndSeparateVideoAndTotalAllowances() {
        String task = TrailerTask.describe("ثورة دونالد ترامب", "ألكسندر دوغين", "ar", "voice-123", 3, 16, 2,
                "model=seedance_2_5, aspect_ratio=16:9, duration=30", false);

        assertThat(task).contains("ثورة دونالد ترامب").contains("ألكسندر دوغين").contains("voice-123")
                .contains("at most 3 video jobs").contains("at most 16 Higgsfield jobs in total")
                .contains("image jobs count toward the total, not toward the video jobs")
                .contains("at most 2 generations").contains("duration=30")
                .contains("/workspace/book.pdf").contains("/mnt/session/outputs/")
                .contains("/workspace/endcard/").doesNotContain("Cover image:")
                .doesNotContain("one accepted"); // mode-neutral: v14 must still be able to run this task
    }

    @Test
    void taskListsTheApprovedVoicesAndTheDefaultAsFallback() {
        String task = TrailerTask.describe("t", "a", "ar", "voice-default", 3, 16, 2, "model=seedance_2_5", false,
                List.of(new TrailerVoice("v1", "Sami", "politics, history"),
                        new TrailerVoice("v2", "Layla", "literature, self-development")));

        assertThat(task).contains("Approved narration voices").contains("You must use one of these voices")
                .contains("v1 — Sami: suits politics, history").contains("v2 — Layla: suits literature, self-development")
                .doesNotContain("default voice_id").doesNotContain("voice-default");
    }

    @Test
    void withoutACatalogTheTaskKeepsTheSingleVoice() {
        String task = TrailerTask.describe("t", "a", "ar", "voice-default", 3, 16, 2, "model=seedance_2_5", false);
        assertThat(task).contains("ElevenLabs voice_id for the Arabic narration: voice-default")
                .doesNotContain("Approved narration voices");
    }

    @Test
    void taskIncludesCoverImageWhenPresent() {
        String task = TrailerTask.describe("ثورة دونالد ترامب", "ألكسندر دوغين", "ar", "voice-123", 3, 16, 2,
                "model=seedance_2_5", true);

        assertThat(task).contains("Cover image: /workspace/cover.jpg");
    }

    @Test
    void blankGenerateArgsFallBackToTheAgentsOwnInstructions() {
        String task = TrailerTask.describe("t", "a", "ar", "voice-123", 3, 16, 2, "", false);
        assertThat(task).contains("(see your instructions)");
    }

    @Test
    void rubricIsOnTheClasspathAndGradeable() throws Exception {
        String rubric = new String(TrailerTaskTest.class.getResourceAsStream("/trailer-agent/rubric.md").readAllBytes());
        assertThat(rubric).contains("1920").contains("eleven_v3").contains("forced-alignment").contains("qc_report.json");
    }
}
