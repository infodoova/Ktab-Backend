package com.doova.ktab.features.tts.service.impl;

import com.doova.ktab.features.tts.cache.TtsAudioCacheEntry;
import com.doova.ktab.features.tts.cache.TtsAudioCacheRepository;
import com.doova.ktab.features.tts.dto.Alignment;
import com.doova.ktab.features.tts.dto.TtsStreamChunk;
import com.doova.ktab.features.tts.service.ElevenLabsTimestampTtsService;
import com.doova.ktab.service.file.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Replays reader narration from storage instead of paying ElevenLabs for the same page again.
 * <p>
 * The same text, voice and model always yield equivalent audio, so the first request generates and stores it and every
 * later one (a retry, a re-opened page, another reader) is served from the cache. A hit returns exactly the
 * {@link TtsStreamChunk} ElevenLabs would have, so callers cannot tell the difference. Caching is best effort: any
 * cache failure is logged and falls through to ElevenLabs, so it can never break narration.
 */
@Slf4j
@Primary
@Service
public class CachingElevenLabsTimestampTtsService implements ElevenLabsTimestampTtsService {

    private static final String AUDIO_CONTENT_TYPE = "audio/mpeg";
    private static final String STORAGE_DIRECTORY = "tts-cache/reader";

    private final ElevenLabsTimestampTtsServiceImpl delegate;
    private final TtsAudioCacheRepository repository;
    private final FileStorageService storage;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    /** Concurrent identical requests (a retry while the first is still generating) share one ElevenLabs call. */
    private final Map<String, Mono<TtsStreamChunk>> inFlight = new ConcurrentHashMap<>();

    public CachingElevenLabsTimestampTtsService(ElevenLabsTimestampTtsServiceImpl delegate,
                                                TtsAudioCacheRepository repository,
                                                FileStorageService storage,
                                                ObjectMapper objectMapper,
                                                @Value("${ktab.tts.cache.enabled:true}") boolean enabled) {
        this.delegate = delegate;
        this.repository = repository;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    public Mono<TtsStreamChunk> streamWithTimestamps(String text, String voiceId, String prevText, String nextText,
                                                     List<String> previousRequestIds, Consumer<String> onRequestId) {
        if (!enabled || text == null || text.isBlank()) {
            return delegate.streamWithTimestamps(text, voiceId, prevText, nextText, previousRequestIds, onRequestId);
        }

        String resolvedVoiceId = delegate.resolveVoiceId(voiceId);
        String key = cacheKey(delegate.timestampsModelId(), resolvedVoiceId, delegate.outputFormat(),
                delegate.voiceSettingsSignature(), text, prevText, nextText);

        return inFlight.computeIfAbsent(key, k -> {
            // The shared entry must outlive the save: between the response and the row being written, a new request
            // would find neither the entry nor a cache row and pay for a second generation.
            AtomicBoolean storePending = new AtomicBoolean(false);
            return lookup(k)
                    .switchIfEmpty(Mono.defer(() -> generateAndStore(k, resolvedVoiceId, text, voiceId, prevText,
                            nextText, previousRequestIds, onRequestId, storePending)))
                    .doFinally(signal -> {
                        if (!storePending.get()) {
                            inFlight.remove(k);
                        }
                    })
                    .cache();
        });
    }

    private Mono<TtsStreamChunk> lookup(String key) {
        return Mono.fromCallable(() -> repository.findById(key).map(this::toChunk).orElse(null))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    log.info("TTS_CACHE_HIT key={}", key);
                    touch(key);
                })
                .onErrorResume(e -> {
                    log.warn("TTS_CACHE_READ_FAILED key={} err={}", key, e.toString());
                    return Mono.empty();
                });
    }

    private Mono<TtsStreamChunk> generateAndStore(String key, String resolvedVoiceId, String text, String requestedVoiceId,
                                                  String prevText, String nextText, List<String> previousRequestIds,
                                                  Consumer<String> onRequestId, AtomicBoolean storePending) {
        log.info("TTS_CACHE_MISS key={}", key);
        return delegate.streamWithTimestamps(text, requestedVoiceId, prevText, nextText, previousRequestIds, onRequestId)
                .doOnNext(chunk -> {
                    if (!isCacheable(chunk)) {
                        return;
                    }
                    // Set before the pipeline's doFinally runs, so that it leaves the entry in place for the save.
                    storePending.set(true);
                    Mono.fromRunnable(() -> store(key, resolvedVoiceId, text, chunk))
                            .subscribeOn(Schedulers.boundedElastic())
                            .doFinally(signal -> inFlight.remove(key))
                            .subscribe(ignored -> {}, e -> log.warn("TTS_CACHE_STORE_FAILED key={} err={}", key, e.toString()));
                });
    }

    /** Without both audio and timings a replay would differ from the real response, so such a result is not cached. */
    private static boolean isCacheable(TtsStreamChunk chunk) {
        return chunk != null && chunk.hasAudio() && chunk.hasAnyAlignment();
    }

    private TtsStreamChunk toChunk(TtsAudioCacheEntry entry) {
        try {
            byte[] audio = storage.getBytes(entry.getStorageKey());
            StoredAlignment alignment = objectMapper.readValue(entry.getAlignmentJson(), StoredAlignment.class);
            return new TtsStreamChunk(Base64.getEncoder().encodeToString(audio), alignment.alignment(),
                    alignment.normalizedAlignment());
        } catch (Exception e) {
            // A row whose audio or timings cannot be read is treated as a miss and regenerated.
            throw new IllegalStateException("Unreadable cache entry " + entry.getCacheKey(), e);
        }
    }

    private void store(String key, String voiceId, String text, TtsStreamChunk chunk) {
        try {
            byte[] audio = Base64.getDecoder().decode(chunk.audioBase64());
            String alignmentJson = objectMapper.writeValueAsString(
                    new StoredAlignment(chunk.alignment(), chunk.normalizedAlignment()));
            String storageKey = storage.storeBytes(audio, AUDIO_CONTENT_TYPE, STORAGE_DIRECTORY, "mp3");
            repository.save(new TtsAudioCacheEntry(key, storageKey, alignmentJson, voiceId,
                    delegate.timestampsModelId(), text.length(), audio.length, 0, null, null));
            log.info("TTS_CACHE_STORED key={} bytes={}", key, audio.length);
        } catch (Exception e) {
            throw new IllegalStateException("Could not cache audio for " + key, e);
        }
    }

    private void touch(String key) {
        Mono.fromRunnable(() -> repository.touch(key, Instant.now()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(ignored -> {}, e -> log.debug("TTS_CACHE_TOUCH_FAILED key={} err={}", key, e.toString()));
    }

    /** SHA-256 of everything that influences the generated audio. */
    static String cacheKey(String modelId, String voiceId, String outputFormat, String voiceSettings, String text,
                           String prevText, String nextText) {
        String joined = String.join("\u0000", modelId, voiceId, outputFormat, voiceSettings, text,
                prevText == null ? "" : prevText, nextText == null ? "" : nextText);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** The two alignment tracks ElevenLabs returns, stored together as JSON. */
    record StoredAlignment(Alignment alignment, Alignment normalizedAlignment) {}
}
