package com.doova.ktab.features.tts.cache;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.features.extraction.BookExtractionQueryService;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.tts.dto.Alignment;
import com.doova.ktab.features.tts.dto.TtsStreamChunk;
import com.doova.ktab.features.tts.service.impl.CachingElevenLabsTimestampTtsService;
import com.doova.ktab.features.tts.service.impl.ElevenLabsTimestampTtsServiceImpl;
import com.doova.ktab.features.tts.ws.ReaderTtsWebSocketHandler;
import com.doova.ktab.service.book.BookTextService;
import com.doova.ktab.service.file.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The reader audio cache against a real Postgres built by Ktab's full Flyway history (so migration V36 and the entity
 * mapping are validated), with the real repository, the real caching service and the real WebSocket handler.
 * Only ElevenLabs (stubbed, so it can be counted) and R2 (in memory) are replaced.
 *
 * <p>The cache does its database work on other threads, in their own transactions, so the usual test transaction is
 * switched off here and the table is emptied by hand.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TtsAudioCacheIT extends StorybookJpaIT {

    @Autowired
    private TtsAudioCacheRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, AtomicInteger> elevenLabsCallsPerText = new ConcurrentHashMap<>();
    private InMemoryStorage storage;
    private ElevenLabsTimestampTtsServiceImpl elevenLabs;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        elevenLabsCallsPerText.clear();
        storage = new InMemoryStorage();
        elevenLabs = mock(ElevenLabsTimestampTtsServiceImpl.class);
        when(elevenLabs.resolveVoiceId(any())).thenAnswer(inv -> inv.getArgument(0) == null ? "default-voice" : inv.getArgument(0));
        when(elevenLabs.timestampsModelId()).thenReturn("eleven_multilingual_v2");
        when(elevenLabs.outputFormat()).thenReturn("mp3_44100_128");
        when(elevenLabs.voiceSettingsSignature()).thenReturn("stability=1.0;similarity_boost=0.75");
        when(elevenLabs.streamWithTimestamps(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            elevenLabsCallsPerText.computeIfAbsent(text, t -> new AtomicInteger()).incrementAndGet();
            return Mono.just(elevenLabsAnswerFor(text)).delayElement(Duration.ofMillis(100));
        });
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    /** A new service each time, as after an application restart: nothing is remembered in memory. */
    private CachingElevenLabsTimestampTtsService newService() {
        return new CachingElevenLabsTimestampTtsService(elevenLabs, repository, storage, objectMapper, true);
    }

    private Mono<TtsStreamChunk> request(CachingElevenLabsTimestampTtsService service, String text) {
        return service.streamWithTimestamps(text, "voice-a", null, null, List.of(), id -> {});
    }

    /** What ElevenLabs would return: binary-looking MP3 bytes and Arabic word timings. */
    private static TtsStreamChunk elevenLabsAnswerFor(String text) {
        byte[] audio = new byte[40_000];
        new Random(text.hashCode()).nextBytes(audio);
        Alignment alignment = new Alignment(
                List.of("ا", "ل", "ف", "ص", "ل"), List.of(0.0, 0.1, 0.2, 0.3, 0.4), List.of(0.1, 0.2, 0.3, 0.4, 0.5));
        return new TtsStreamChunk(Base64.getEncoder().encodeToString(audio), alignment, alignment);
    }

    private void awaitRows(long expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(repository.count()).isEqualTo(expected));
    }

    @Test
    void anEntryRoundTripsThroughTheRealTableAndHitsAreCounted() {
        TtsAudioCacheEntry entry = new TtsAudioCacheEntry("a".repeat(64), "tts-cache/reader/x.mp3",
                "{\"alignment\":null}", "voice", "model", 12, 34L, 0, null, null);

        repository.save(entry);
        int updated = repository.touch(entry.getCacheKey(), java.time.Instant.now());

        TtsAudioCacheEntry loaded = repository.findById(entry.getCacheKey()).orElseThrow();
        assertThat(updated).isEqualTo(1);
        assertThat(loaded.getHitCount()).isEqualTo(1);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getAudioBytes()).isEqualTo(34L);
    }

    @Test
    void firstRequestGeneratesAndASecondAfterARestartReplaysIdenticalAudioAndTimings() {
        TtsStreamChunk first = request(newService(), "الفصل الأول").block(Duration.ofSeconds(10));
        awaitRows(1);

        TtsStreamChunk replayed = request(newService(), "الفصل الأول").block(Duration.ofSeconds(10));

        assertThat(replayed).isEqualTo(first);
        assertThat(elevenLabsCallsPerText.get("الفصل الأول").get()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findAll().get(0).getHitCount()).isEqualTo(1));
        assertThat(storage.objects).hasSize(1);
    }

    @Test
    void differentVoiceOrTextIsAnotherEntry() {
        var service = newService();
        service.streamWithTimestamps("page", "voice-a", null, null, List.of(), id -> {}).block(Duration.ofSeconds(10));
        service.streamWithTimestamps("page", "voice-b", null, null, List.of(), id -> {}).block(Duration.ofSeconds(10));
        request(service, "other page").block(Duration.ofSeconds(10));

        awaitRows(3);
        assertThat(elevenLabsCallsPerText.get("page").get()).isEqualTo(2);
    }

    @Test
    void fiftyConcurrentRequestsGenerateEachPageOnceThenAnotherFiftyAreAllHits() {
        int pages = 10;
        var service = newService();

        List<TtsStreamChunk> firstWave = wave(service, pages, 50);

        assertThat(firstWave).hasSize(50);
        awaitRows(pages);
        assertThat(elevenLabsCallsPerText.values()).hasSize(pages).allSatisfy(n -> assertThat(n.get()).isEqualTo(1));

        List<TtsStreamChunk> secondWave = wave(newService(), pages, 50);

        assertThat(secondWave).containsExactlyInAnyOrderElementsOf(firstWave);
        assertThat(elevenLabsCallsPerText.values()).allSatisfy(n -> assertThat(n.get()).isEqualTo(1));
        // Hits are counted per lookup, and overlapping identical requests share one lookup, so the total is somewhere
        // between one per page and one per request.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findAll()).hasSize(pages).allSatisfy(row -> assertThat(row.getHitCount()).isGreaterThanOrEqualTo(1)));
        assertThat(repository.findAll().stream().mapToLong(TtsAudioCacheEntry::getHitCount).sum()).isBetween((long) pages, 50L);
    }

    private List<TtsStreamChunk> wave(CachingElevenLabsTimestampTtsService service, int pages, int requests) {
        return Flux.range(0, requests)
                .flatMap(i -> request(service, "page " + (i % pages)).subscribeOn(Schedulers.parallel()), requests)
                .collectList()
                .block(Duration.ofSeconds(30));
    }

    @Test
    void anEntryWhoseStoredAudioIsGoneIsRegeneratedInsteadOfFailing() {
        request(newService(), "page").block(Duration.ofSeconds(10));
        awaitRows(1);
        storage.objects.clear();

        TtsStreamChunk result = request(newService(), "page").block(Duration.ofSeconds(10));

        assertThat(result).isEqualTo(elevenLabsAnswerFor("page"));
        assertThat(elevenLabsCallsPerText.get("page").get()).isEqualTo(2);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(storage.objects).hasSize(1));
    }

    @Test
    void theHandlerSendsExactlyTheSameMessagesForAHitAsForAMiss() throws Exception {
        var handler = new ReaderTtsWebSocketHandler(newService(), mock(BookTextService.class),
                mock(BookExtractionQueryService.class), objectMapper, new SimpleMeterRegistry());

        List<String> miss = narrate(handler, "session-miss");
        awaitRows(1);
        List<String> hit = narrate(handler, "session-hit");

        assertThat(miss).anyMatch(m -> m.startsWith("text:{\"type\":\"alignment\""));
        assertThat(miss).anyMatch(m -> m.startsWith("binary:"));
        assertThat(miss).last().isEqualTo("text:{\"type\":\"complete\"}");
        assertThat(hit).isEqualTo(miss);
        assertThat(elevenLabsCallsPerText.get("Hello reader").get()).isEqualTo(1);
    }

    /** Runs one narration through the real handler and returns every message it sent, text and binary alike. */
    private List<String> narrate(ReaderTtsWebSocketHandler handler, String sessionId) throws Exception {
        List<String> sent = new CopyOnWriteArrayList<>();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            WebSocketMessage<?> message = invocation.getArgument(0);
            if (message instanceof TextMessage text) {
                sent.add("text:" + text.getPayload());
            } else if (message instanceof BinaryMessage binary) {
                ByteBuffer payload = binary.getPayload().asReadOnlyBuffer();
                byte[] bytes = new byte[payload.remaining()];
                payload.get(bytes);
                sent.add("binary:" + Base64.getEncoder().encodeToString(bytes));
            }
            return null;
        }).when(session).sendMessage(any(WebSocketMessage.class));

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage(objectMapper.writeValueAsString(Map.of(
                "action", "stream", "bookId", 1, "voiceId", "voice-a", "text", "Hello reader"))));
        await().atMost(Duration.ofSeconds(15)).until(() -> sent.stream().anyMatch(m -> m.equals("text:{\"type\":\"complete\"}")));
        return new ArrayList<>(sent);
    }

    /** Object storage held in memory: the tests must not write to the real R2 bucket. */
    private static final class InMemoryStorage implements FileStorageService {
        final ConcurrentHashMap<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public String storeBytes(byte[] content, String contentType, String directoryKey, String extension) {
            String key = directoryKey + "/" + UUID.randomUUID() + "." + extension;
            objects.put(key, content);
            return key;
        }

        @Override
        public byte[] getBytes(String key) {
            byte[] bytes = objects.get(key);
            if (bytes == null) {
                throw new IllegalStateException("No such object: " + key);
            }
            return bytes;
        }

        @Override
        public String storeFile(MultipartFile file, String directoryKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteFile(String keyName) {
            objects.remove(keyName);
        }

        @Override
        public String getFileUrl(String keyName, UrlStrategy urlStrategy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getPreSignedDownloadUrl(String keyName, Duration duration, String downloadFilename) {
            throw new UnsupportedOperationException();
        }
    }
}
