package com.doova.ktab.features.trailer.agent;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real session, real spend (Claude + Higgsfield + ElevenLabs). Needs the control plane from Task 1 and a connected
 * Higgsfield vault credential. Run: TRAILER_AGENT_LIVE=true TRAILER_LIVE_PDF=/path/book.pdf KTAB_TRAILER_* ANTHROPIC_API_KEY
 */
@EnabledIfEnvironmentVariable(named = "TRAILER_AGENT_LIVE", matches = "true")
class TrailerAgentLiveTest {

    @Test
    void startsASessionAndPrintsTheTraceUrl() throws Exception {
        TrailerProperties p = new TrailerProperties();
        p.setAnthropicApiKey(System.getenv("ANTHROPIC_API_KEY"));
        p.setAgentId(System.getenv("KTAB_TRAILER_AGENT_ID"));
        p.setAgentVersion(Integer.parseInt(System.getenv("KTAB_TRAILER_AGENT_VERSION")));
        p.setEnvironmentId(System.getenv("KTAB_TRAILER_ENVIRONMENT_ID"));
        p.setVaultId(System.getenv("KTAB_TRAILER_VAULT_ID"));
        p.setVoices(System.getenv("KTAB_TRAILER_VOICES"));
        AnthropicTrailerAgentGateway gateway = new AnthropicTrailerAgentGateway(p);

        String fileId = gateway.uploadBook(Path.of(System.getenv("TRAILER_LIVE_PDF")));
        String sessionId = gateway.startSession(0L, fileId,
                TrailerTask.describe("Live test", "Test author", "ar", p.getMaxVideoJobs(),
                        p.getMaxHiggsfieldGenerations(), p.getHiggsfieldMaxInFlight(), p.getHiggsfieldGenerateArgs(), false,
                        p.voiceCatalog()),
                TrailerTask.rubric());

        System.out.println("Watch: " + gateway.traceUrl(sessionId));
        assertThat(sessionId).startsWith("sesn_");
    }
}
