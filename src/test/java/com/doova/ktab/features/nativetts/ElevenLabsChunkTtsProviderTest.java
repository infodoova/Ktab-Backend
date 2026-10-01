package com.doova.ktab.features.nativetts;

import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElevenLabsChunkTtsProviderTest {

    private static final String OK = "{\"audio_base64\":\"AAEC\",\"alignment\":{\"characters\":[\"ا\",\"ب\"],"
            + "\"character_start_times_seconds\":[0,0.2],\"character_end_times_seconds\":[0.2,0.5]}}";

    private HttpServer server;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<int[]> script = new ArrayList<>();   // {status, bodyIndex}: 0 = OK, 1 = 429, 2 = 400
    private int call;
    private NativeTtsProperties props;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", ex -> {
            paths.add(ex.getRequestURI().toString());
            keys.add(ex.getRequestHeaders().getFirst("xi-api-key"));
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int[] step = script.get(Math.min(call++, script.size() - 1));
            String body = step[0] == 200 ? OK : step[0] == 429 ? "{\"detail\":\"rate limited\"}" : "{\"detail\":\"text too long\"}";
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("content-type", "application/json");
            ex.sendResponseHeaders(step[0], out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        props = new NativeTtsProperties();
        props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        props.setApiKey("key");
        props.setVoiceId("voice-1");
        props.setModelId("eleven_multilingual_v2");
        props.setRetryBackoffMillis(1);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void retriesOn429ThenParsesAudioAndAlignment() {
        script.add(new int[]{429});
        script.add(new int[]{200});

        ChunkAudio a = new ElevenLabsChunkTtsProvider(props).synthesize("اب", "", "");

        assertThat(a.mp3()).containsExactly(0, 1, 2);
        assertThat(a.chars()).isEqualTo("اب");
        assertThat(a.startSec()).containsExactly(0.0, 0.2);
        assertThat(a.endSec()).containsExactly(0.2, 0.5);
        assertThat(paths).hasSize(2).allSatisfy(p -> assertThat(p)
                .isEqualTo("/v1/text-to-speech/voice-1/with-timestamps?output_format=mp3_44100_128"));
        assertThat(keys).containsOnly("key");
    }

    @Test
    void sendsTheModelTextAndOnlyTheContextThatExists() {
        script.add(new int[]{200});
        script.add(new int[]{200});
        ElevenLabsChunkTtsProvider provider = new ElevenLabsChunkTtsProvider(props);

        provider.synthesize("اب", "", "");
        provider.synthesize("اب", "قبل", "بعد");

        assertThat(bodies.get(0)).contains("\"model_id\":\"eleven_multilingual_v2\"").contains("\"text\":\"اب\"")
                .doesNotContain("previous_text").doesNotContain("next_text");
        assertThat(bodies.get(1)).contains("\"previous_text\":\"قبل\"").contains("\"next_text\":\"بعد\"");
    }

    @Test
    void aClientErrorIsNotRetriedAndCarriesTheServersReason() {
        script.add(new int[]{400});

        assertThatThrownBy(() -> new ElevenLabsChunkTtsProvider(props).synthesize("اب", "", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("400").hasMessageContaining("text too long");
        assertThat(paths).hasSize(1);
    }

    @Test
    void givesUpAfterTheConfiguredRetries() {
        script.add(new int[]{429});
        props.setMaxRetries(2);

        assertThatThrownBy(() -> new ElevenLabsChunkTtsProvider(props).synthesize("اب", "", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("429");
        assertThat(paths).hasSize(3); // first try + 2 retries
    }
}
