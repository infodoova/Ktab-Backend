package com.doova.ktab.features.nativetts;

import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** ElevenLabs' standard text-to-speech with timestamps: POST /v1/text-to-speech/{voice}/with-timestamps. */
@Component
public class ElevenLabsChunkTtsProvider implements ChunkTtsProvider {

    private final NativeTtsProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper json = new ObjectMapper();

    public ElevenLabsChunkTtsProvider(NativeTtsProperties props) {
        this.props = props;
    }

    @Override
    public ChunkAudio synthesize(String text, String previousText, String nextText) {
        if (props.getVoiceId() == null || props.getVoiceId().isBlank()) {
            throw new IllegalStateException("No narrator voice configured: set KTAB_NATIVE_TTS_VOICE_ID");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        body.put("model_id", props.getModelId());
        if (previousText != null && !previousText.isEmpty()) {
            body.put("previous_text", previousText);
        }
        if (nextText != null && !nextText.isEmpty()) {
            body.put("next_text", nextText);
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(props.getBaseUrl() + "/v1/text-to-speech/" + props.getVoiceId()
                            + "/with-timestamps?output_format=" + props.getOutputFormat()))
                    .timeout(Duration.ofMinutes(5))
                    .header("xi-api-key", props.getApiKey() == null ? "" : props.getApiKey())
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the TTS request", e);
        }
        return parse(send(request));
    }

    private String send(HttpRequest request) {
        String lastFailure = "no attempt made";
        for (int attempt = 0; attempt <= props.getMaxRetries(); attempt++) {
            if (attempt > 0) {
                sleep(props.getRetryBackoffMillis() * (1L << (attempt - 1)));
            }
            try {
                HttpResponse<String> r = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = r.statusCode();
                if (status == 200) {
                    return r.body();
                }
                lastFailure = "HTTP " + status + ": " + r.body();
                if (status != 429 && status < 500) {
                    throw new IllegalStateException("ElevenLabs TTS failed with " + lastFailure);
                }
            } catch (IOException e) {
                lastFailure = "I/O error: " + e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for ElevenLabs", e);
            }
        }
        throw new IllegalStateException("ElevenLabs TTS failed after " + (props.getMaxRetries() + 1) + " attempts, last: " + lastFailure);
    }

    private ChunkAudio parse(String body) {
        try {
            JsonNode root = json.readTree(body);
            JsonNode alignment = root.hasNonNull("alignment") ? root.get("alignment") : root.get("normalized_alignment");
            if (alignment == null) {
                throw new IllegalStateException("ElevenLabs returned no alignment");
            }
            StringBuilder chars = new StringBuilder();
            for (JsonNode c : alignment.get("characters")) {
                chars.append(c.asText());
            }
            return new ChunkAudio(Base64.getDecoder().decode(root.get("audio_base64").asText()), chars.toString(),
                    doubles(alignment.get("character_start_times_seconds")), doubles(alignment.get("character_end_times_seconds")));
        } catch (IOException e) {
            throw new IllegalStateException("ElevenLabs returned an unreadable response", e);
        }
    }

    private static double[] doubles(JsonNode array) {
        double[] out = new double[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).asDouble();
        }
        return out;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off", e);
        }
    }
}
