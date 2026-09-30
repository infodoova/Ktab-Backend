// src/test/java/com/doova/ktab/features/trailer/ControlPlaneFilesTest.java
package com.doova.ktab.features.trailer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ControlPlaneFilesTest {

    private final ObjectMapper json = new ObjectMapper();
    private final Path dir = Path.of("ops/trailer-agent");

    @Test
    void environmentInstallsFfmpegAndPoppler() throws Exception {
        JsonNode env = json.readTree(dir.resolve("environment.json").toFile());
        assertThat(env.at("/config/type").asText()).isEqualTo("cloud");
        assertThat(env.at("/config/packages/apt").toString()).contains("ffmpeg").contains("poppler-utils");
    }

    @Test
    void mcpToolsRunWithoutApprovalOtherwiseEverySessionStalls() throws Exception {
        JsonNode agent = json.readTree(dir.resolve("agent.json").toFile());
        assertThat(agent.at("/model/id").asText()).isEqualTo("claude-opus-5-5");
        assertThat(agent.at("/mcp_servers/0/url").asText()).isEqualTo("https://mcp.higgsfield.ai/mcp");
        JsonNode mcpToolset = null;
        for (JsonNode tool : agent.get("tools")) {
            if ("mcp_toolset".equals(tool.path("type").asText())) {
                mcpToolset = tool;
            }
        }
        assertThat(mcpToolset).isNotNull();
        assertThat(mcpToolset.at("/default_config/permission_policy/type").asText()).isEqualTo("always_allow");
    }

    @Test
    void noSecretsAreCommitted() throws Exception {
        for (String f : new String[]{"environment.json", "agent.json", "system-prompt.md", "setup.sh", "update-agent.sh"}) {
            String text = Files.readString(dir.resolve(f));
            assertThat(text).doesNotContainPattern("sk-ant-[A-Za-z0-9]").doesNotContainPattern("sk_[0-9a-f]{20,}")
                    .doesNotContain("whsec_");
        }
    }

    @Test
    void systemPromptStatesTheNonNegotiables() throws Exception {
        String prompt = Files.readString(dir.resolve("system-prompt.md"));
        assertThat(prompt).contains("eleven_v3").contains("forced-alignment").contains("/mnt/session/outputs/")
                .contains("qc_report.json").contains("captions_ar.srt");
    }

    @Test
    void systemPromptPreventsHiggsfieldWaste() throws Exception {
        // D5: the first real run wasted 4 of 11 calls on concurrency rejections and preset suggestions.
        String prompt = Files.readString(dir.resolve("system-prompt.md"));
        assertThat(prompt).contains("exactly the arguments given in the task message")
                .contains("Concurrency").contains("429").contains("wait 60 seconds")
                .contains("Never put text-bearing things in a generated shot");
    }

    @Test
    void systemPromptMakesTheWholeMontageInOneGeneration() throws Exception {
        String prompt = Files.readString(dir.resolve("system-prompt.md"));
        assertThat(prompt).contains("exactly one accepted Seedance 2.5 generation")
                .contains("DIRECTING LANGUAGE").contains("GLOBAL LOOK").contains("SHOT TIMELINE")
                .contains("MIN_INSERT_SECONDS").contains("audio first")
                .contains("scdet").contains("±0.4").contains("picture is never padded")
                .contains("never re-edit the picture")
                .doesNotContain("stop_mode=clone").doesNotContain("minterpolate may");
    }

    @Test
    void systemPromptCarriesTheMasterDirectingLanguage() throws Exception {
        // Source: docs/trailer/master-directing-prompt.md (2026-09-30).
        String prompt = Files.readString(dir.resolve("system-prompt.md"));
        assertThat(prompt).contains("central dramatic question").contains("emotional anchor")
                .contains("Slow anticipation → fast fragments → sustained reveal")
                .contains("never a frozen frame").contains("Slow camera movement does not automatically mean slow-motion")
                .contains("Avoid repeating the same push-in").contains("brief passage through darkness")
                .contains("reveal enough to create desire");
    }

    @Test
    void systemPromptNeverBurnsCaptionsAndUsesTheRenderedEndCard() throws Exception {
        String prompt = Files.readString(dir.resolve("system-prompt.md"));
        assertThat(prompt).contains("Do not burn captions").contains("/workspace/endcard/scrim.png")
                .contains("/workspace/endcard/logo.png").contains("Never use drawtext").contains("st=27.5")
                .doesNotContain("subtitles=captions_ar.srt").doesNotContain("trailer_clean.mp4");
    }

    @Test
    void rubricGradesTheSingleGenerationTrailer() throws Exception {
        String rubric = Files.readString(Path.of("src/main/resources/trailer-agent/rubric.md"));
        assertThat(rubric).contains("single Seedance 2.5 generation").contains("no burned-in captions")
                .contains("central dramatic question").contains("Fictional characters appear only for fiction books")
                .doesNotContain("trailer_clean.mp4").doesNotContain("The imagery is symbolic");
    }
}
