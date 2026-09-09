package com.doova.ktab.features.tts.service.impl;

import com.doova.ktab.features.tts.config.ElevenLabsProperties;
import com.doova.ktab.features.tts.dto.TtsStreamChunk;
import com.doova.ktab.features.tts.exception.ElevenLabsQuotaExceededException;
import com.doova.ktab.features.tts.service.ElevenLabsTimestampTtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@Slf4j
public class ElevenLabsTimestampTtsServiceImpl implements ElevenLabsTimestampTtsService {

    private final WebClient webClient;
    private final ElevenLabsProperties props;

    public ElevenLabsTimestampTtsServiceImpl(WebClient.Builder webClientBuilder, ElevenLabsProperties props) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .build();

        this.props = props;
        this.webClient = webClientBuilder
                .baseUrl(firstNonBlank(props.getBaseUrl(), "https://api.elevenlabs.io"))
                .exchangeStrategies(strategies)
                .defaultHeader("xi-api-key", props.getApiKey())
                .build();
    }

    @Override
    public Mono<TtsStreamChunk> streamWithTimestamps(String text, String voiceId, String prevText, String nextText, List<String> previousRequestIds, Consumer<String> onRequestId) {
        log.info(">>> EL_SERVICE_REQ voiceId={} textLen={} prevIds={}", voiceId, (text != null ? text.length() : 0), previousRequestIds);
        String preferredModel = firstNonBlank(props.getModelId(), "eleven_multilingual_v2");
        String fallbackModel = firstNonBlank(props.getTimestampsFallbackModelId(), "eleven_multilingual_v2");
        preferredModel = "eleven_multilingual_v2";

        log.debug("ElevenLabs timestamps models preferred='{}' fallback='{}'", preferredModel, fallbackModel);

        String finalPreferredModel = preferredModel;
        return streamWithTimestampsModel(text, voiceId, prevText, nextText, preferredModel, previousRequestIds, onRequestId).onErrorResume(e -> {
            // Recovery: If 400 Bad Request AND we sent previous_ids, try clearing context first (stay on preferred model)
            if (e instanceof WebClientResponseException.BadRequest && previousRequestIds != null && !previousRequestIds.isEmpty()) {
                log.warn("⚠️ ElevenLabs 400 Bad Request with context. Retrying CLEARED context. prevIds={}", previousRequestIds);
                return streamWithTimestampsModel(text, voiceId, null, null, finalPreferredModel, null, onRequestId);
            }

            if (shouldFallbackModel(finalPreferredModel, fallbackModel, e)) {
                log.warn("ElevenLabs timestamps model '{}' rejected; falling back to '{}'", finalPreferredModel, fallbackModel);
                return streamWithTimestampsModel(text, voiceId, prevText, nextText, fallbackModel, null, onRequestId);
            }
            return Mono.error(e);
        });
    }

    private Mono<TtsStreamChunk> streamWithTimestampsModel(String text, String voiceId, String prevText, String nextText, String modelId, List<String> previousRequestIds, Consumer<String> onRequestId) {
        log.info("--- EL_PREPARE_REQ model={} hasPrevText={} hasNextText={}", modelId, (prevText != null && !prevText.isBlank()), (nextText != null && !nextText.isBlank()));

        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("model_id", modelId);

        // Keeps natural flow between chunks
        if (prevText != null && !prevText.isBlank()) {
            body.put("previous_text", prevText);
        }
        if (nextText != null && !nextText.isBlank()) {
            body.put("next_text", nextText);
        }

        if (previousRequestIds != null && !previousRequestIds.isEmpty()) {
            body.put("previous_request_ids", previousRequestIds.size() > 3 ? previousRequestIds.subList(previousRequestIds.size() - 3, previousRequestIds.size()) : previousRequestIds);
        }

        body.put("voice_settings", Map.of("stability", 1.0, "similarity_boost", 0.75));

        return webClient.post().uri(uri -> uri.path("/v1/text-to-speech/{voiceId}/with-timestamps").queryParam("output_format", firstNonBlank(props.getOutputFormat(), "mp3_44100_128")).build(voiceId))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.value() == 401 || status.value() == 402, response -> response.bodyToMono(String.class).flatMap(err -> {
                    log.error("ELEVENLABS_CREDITS_EXHAUSTED response={}", err);
                    return Mono.error(new ElevenLabsQuotaExceededException(err));
                }))
                .toEntity(TtsStreamChunk.class)
                .mapNotNull(entity -> {
                    String requestId = entity.getHeaders().getFirst("request-id");
                    log.info("<<< EL_RECV_HEADER requestId={}", requestId);
                    if (requestId != null && onRequestId != null) {
                        onRequestId.accept(requestId);
                    }
                    return entity.getBody();
                })
                .doOnSubscribe(s -> log.info("🔔 TTS_POST text=\"{}...\"", text.substring(0, Math.min(20, text.length()))))
                .doOnSuccess(chunk -> {
                    if (chunk != null) {
                        log.info("✅ TTS_COMPLETE audio={} alignment={}", chunk.hasAudio(), chunk.hasAnyAlignment());
                    } else {
                        log.warn("⚠️ TTS_COMPLETE_NULL");
                    }
                })
                .doOnError(e -> log.error("❌ TTS_ERROR", e))
                // Network resilience
                .retryWhen(Retry.backoff(2, Duration.ofMillis(500)).filter(e -> e instanceof java.net.SocketException || e.getMessage().contains("Connection reset")))
                .doOnError(e -> log.error(String.valueOf(e)));
    }

    private static String firstNonBlank(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }

    private static boolean shouldFallbackModel(String preferredModel, String fallbackModel, Throwable e) {
        if (preferredModel == null || fallbackModel == null) return false;
        if (preferredModel.equalsIgnoreCase(fallbackModel)) return false;

        // Never fallback on quota / auth
        if (e instanceof ElevenLabsQuotaExceededException) return false;

        // Never fallback on network / transport errors
        if (e instanceof java.net.SocketException) return false;
        if (e instanceof java.util.concurrent.TimeoutException) return false;

        // Never fallback on decoding / mapping errors
        if (e instanceof com.fasterxml.jackson.core.JsonProcessingException) return false;
        if (e instanceof org.springframework.core.codec.DecodingException) return false;

        // Fallback ONLY when ElevenLabs rejects the model
        if (e instanceof WebClientResponseException w) {
            int code = w.getStatusCode().value();

            // ElevenLabs model incompatibility signals
            if (code == 400 || code == 404 || code == 422) {
                String body = w.getResponseBodyAsString().toLowerCase();
                return body.contains("model") || body.contains("unsupported") || body.contains("not available") || body.contains("not supported");
            }
        }

        return false;
    }
}
