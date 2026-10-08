package com.doova.ktab.features.tts.service.impl;

import com.doova.ktab.features.tts.cache.TtsAudioCacheEntry;
import com.doova.ktab.features.tts.cache.TtsAudioCacheRepository;
import com.doova.ktab.features.tts.dto.Alignment;
import com.doova.ktab.features.tts.dto.TtsStreamChunk;
import com.doova.ktab.service.file.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachingElevenLabsTimestampTtsServiceTest {

    private static final byte[] AUDIO = "fake-mp3-bytes".getBytes();
    private static final Alignment ALIGNMENT =
            new Alignment(List.of("a", "b"), List.of(0.0, 0.1), List.of(0.1, 0.2));

    private ElevenLabsTimestampTtsServiceImpl delegate;
    private TtsAudioCacheRepository repository;
    private FileStorageService storage;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        delegate = mock(ElevenLabsTimestampTtsServiceImpl.class);
        repository = mock(TtsAudioCacheRepository.class);
        storage = mock(FileStorageService.class);
        when(delegate.resolveVoiceId(any())).thenAnswer(inv -> inv.getArgument(0) == null ? "default-voice" : inv.getArgument(0));
        when(delegate.timestampsModelId()).thenReturn("model-1");
        when(delegate.outputFormat()).thenReturn("mp3_44100_128");
        when(delegate.voiceSettingsSignature()).thenReturn("settings");
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(storage.storeBytes(any(), anyString(), anyString(), anyString())).thenReturn("tts-cache/reader/x.mp3");
    }

    private CachingElevenLabsTimestampTtsService service(boolean enabled) {
        return new CachingElevenLabsTimestampTtsService(delegate, repository, storage, objectMapper, enabled);
    }

    private static TtsStreamChunk generated() {
        return new TtsStreamChunk(Base64.getEncoder().encodeToString(AUDIO), ALIGNMENT, ALIGNMENT);
    }

    private Mono<TtsStreamChunk> call(CachingElevenLabsTimestampTtsService service, String text, String voice) {
        return service.streamWithTimestamps(text, voice, null, null, List.of(), id -> {});
    }

    @Test
    void missGeneratesOnceAndStoresAudioAndTimings() {
        when(delegate.streamWithTimestamps(any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(generated()));

        TtsStreamChunk result = call(service(true), "page text", "voice-a").block();

        assertThat(result).isEqualTo(generated());
        verify(storage, timeout(2000)).storeBytes(eq(AUDIO), eq("audio/mpeg"), anyString(), eq("mp3"));
        var saved = ArgumentCaptor.forClass(TtsAudioCacheEntry.class);
        verify(repository, timeout(2000)).save(saved.capture());
        assertThat(saved.getValue().getStorageKey()).isEqualTo("tts-cache/reader/x.mp3");
        assertThat(saved.getValue().getAudioBytes()).isEqualTo(AUDIO.length);
        assertThat(saved.getValue().getAlignmentJson()).contains("character_start_times_seconds");
    }

    @Test
    void hitReplaysStoredAudioWithoutCallingElevenLabs() throws Exception {
        String json = objectMapper.writeValueAsString(new CachingElevenLabsTimestampTtsService.StoredAlignment(ALIGNMENT, ALIGNMENT));
        var entry = new TtsAudioCacheEntry("k", "tts-cache/reader/x.mp3", json, "voice-a", "model-1", 9, AUDIO.length, 0, null, null);
        when(repository.findById(anyString())).thenReturn(Optional.of(entry));
        when(storage.getBytes("tts-cache/reader/x.mp3")).thenReturn(AUDIO);

        TtsStreamChunk result = call(service(true), "page text", "voice-a").block();

        assertThat(result).isEqualTo(generated());
        verify(delegate, never()).streamWithTimestamps(any(), any(), any(), any(), any(), any());
        verify(repository, timeout(2000)).touch(anyString(), any());
    }

    @Test
    void concurrentIdenticalRequestsShareOneElevenLabsCall() {
        AtomicInteger calls = new AtomicInteger();
        when(delegate.streamWithTimestamps(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> Mono.just(generated()).delayElement(Duration.ofMillis(300)).doOnSubscribe(s -> calls.incrementAndGet()));
        var service = service(true);

        Mono<TtsStreamChunk> first = call(service, "page text", "voice-a");
        Mono<TtsStreamChunk> second = call(service, "page text", "voice-a");

        List<TtsStreamChunk> results = Mono.zip(first, second).map(t -> List.of(t.getT1(), t.getT2())).block();
        assertThat(results).hasSize(2);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void requestArrivingWhileTheResultIsBeingSavedDoesNotGenerateAgain() {
        AtomicInteger calls = new AtomicInteger();
        when(delegate.streamWithTimestamps(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> Mono.fromSupplier(() -> {
                    calls.incrementAndGet();
                    return generated();
                }));
        // The save is slow, and the repository does not know the row yet, exactly like the window after a response.
        when(storage.storeBytes(any(), anyString(), anyString(), anyString())).thenAnswer(inv -> {
            Thread.sleep(500);
            return "tts-cache/reader/x.mp3";
        });
        var service = service(true);

        call(service, "page text", "voice-a").block();
        TtsStreamChunk second = call(service, "page text", "voice-a").block();

        assertThat(second).isEqualTo(generated());
        assertThat(calls.get()).isEqualTo(1);
        verify(repository, timeout(3000)).save(any());
    }

    @Test
    void fiftyConcurrentMixedRequestsGenerateEachPageOnce() {
        int pages = 10;
        int readersPerPage = 5;
        ConcurrentHashMap<String, AtomicInteger> callsPerPage = new ConcurrentHashMap<>();
        when(delegate.streamWithTimestamps(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            String page = inv.getArgument(0);
            callsPerPage.computeIfAbsent(page, p -> new AtomicInteger()).incrementAndGet();
            return Mono.just(generated()).delayElement(Duration.ofMillis(150));
        });
        var service = service(true);

        List<TtsStreamChunk> results = Flux.range(0, pages * readersPerPage)
                .flatMap(i -> call(service, "page " + (i % pages), "voice-a").subscribeOn(Schedulers.parallel()), pages * readersPerPage)
                .collectList()
                .block(Duration.ofSeconds(20));

        assertThat(results).hasSize(pages * readersPerPage).allMatch(generated()::equals);
        assertThat(callsPerPage).hasSize(pages);
        assertThat(callsPerPage.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
    }

    @Test
    void cacheFailuresFallThroughToElevenLabs() {
        when(repository.findById(anyString())).thenThrow(new IllegalStateException("db down"));
        when(storage.storeBytes(any(), anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("r2 down"));
        when(delegate.streamWithTimestamps(any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(generated()));

        TtsStreamChunk result = call(service(true), "page text", "voice-a").block();

        assertThat(result).isEqualTo(generated());
    }

    @Test
    void resultsWithoutAudioOrTimingsAreNotCached() {
        when(delegate.streamWithTimestamps(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(new TtsStreamChunk(null, null, null)));

        call(service(true), "page text", "voice-a").block();

        verify(storage, never()).storeBytes(any(), anyString(), anyString(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void disabledCachePassesStraightThrough() {
        when(delegate.streamWithTimestamps(any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(generated()));

        call(service(false), "page text", "voice-a").block();

        verify(repository, never()).findById(anyString());
        verify(storage, never()).storeBytes(any(), anyString(), anyString(), anyString());
    }

    @Test
    void keyChangesWithTextVoiceAndNeighbours() {
        String base = CachingElevenLabsTimestampTtsService.cacheKey("m", "v", "f", "s", "text", null, null);

        assertThat(CachingElevenLabsTimestampTtsService.cacheKey("m", "v", "f", "s", "text", null, null)).isEqualTo(base);
        assertThat(CachingElevenLabsTimestampTtsService.cacheKey("m", "v", "f", "s", "other", null, null)).isNotEqualTo(base);
        assertThat(CachingElevenLabsTimestampTtsService.cacheKey("m", "w", "f", "s", "text", null, null)).isNotEqualTo(base);
        assertThat(CachingElevenLabsTimestampTtsService.cacheKey("m", "v", "f", "s", "text", "prev", null)).isNotEqualTo(base);
        assertThat(base).hasSize(64);
    }
}
