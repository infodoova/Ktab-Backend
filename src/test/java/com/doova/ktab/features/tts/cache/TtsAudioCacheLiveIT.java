package com.doova.ktab.features.tts.cache;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.features.tts.dto.TtsStreamChunk;
import com.doova.ktab.features.tts.service.ElevenLabsTimestampTtsService;
import com.doova.ktab.features.tts.service.impl.CachingElevenLabsTimestampTtsService;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End to end against the real stack: the application context of the development profile, the real database, the real
 * ElevenLabs API and the real Cloudflare R2 bucket. It makes one short, paid ElevenLabs call, leaves the cached audio
 * in the bucket (under {@code tts-cache/reader/}) and its row in {@code tbl_tts_audio_cache}, and prints where.
 *
 * <p>Never runs by default: set {@code KTAB_LIVE_TTS_IT=true} and select it with {@code -Dtest=TtsAudioCacheLiveIT}.
 */
@SpringBootTest
@ActiveProfiles("development")
@EnabledIfEnvironmentVariable(named = "KTAB_LIVE_TTS_IT", matches = "true")
class TtsAudioCacheLiveIT {

    /** The voice the reader sends in production (seen in the VPS logs). */
    private static final String VOICE_ID = "aCChyB4P5WEomwRsOKRh";
    /** A rerun with the same sentence starts from the cached entry; set KTAB_LIVE_TTS_IT_TEXT for a fresh miss. */
    private static final String TEXT = System.getenv().getOrDefault("KTAB_LIVE_TTS_IT_TEXT",
            "مرحباً بكم في كتاب، هذا اختبار قصير للقراءة الصوتية.");

    @Autowired
    private ElevenLabsTimestampTtsService tts;

    @Autowired
    private TtsAudioCacheRepository repository;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private FileStorageService storage;

    @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName}}")
    private String bucket;

    private static void say(String message) {
        System.out.println("LIVE_TTS_IT " + message);
    }

    @Test
    void realElevenLabsAudioIsCachedInRealR2AndReplayedFromIt() {
        assertThat(tts).as("the reader must be wired to the caching service").isInstanceOf(CachingElevenLabsTimestampTtsService.class);

        long started = System.nanoTime();
        TtsStreamChunk first = tts.streamWithTimestamps(TEXT, VOICE_ID, null, null, List.of(), id -> {}).block(Duration.ofSeconds(90));
        long firstMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(first).isNotNull();
        assertThat(first.hasAudio()).isTrue();
        assertThat(first.hasAnyAlignment()).isTrue();
        byte[] generated = Base64.getDecoder().decode(first.audioBase64());
        assertThat(isMp3(generated)).as("ElevenLabs returned MP3 data").isTrue();

        // The save runs after the response, so wait for this audio's row to appear.
        await().atMost(Duration.ofSeconds(30)).until(() -> findRow(generated.length).isPresent());
        TtsAudioCacheEntry row = findRow(generated.length).orElseThrow();
        assertThat(row.getStorageKey()).startsWith("tts-cache/reader/");

        HeadObjectResponse object = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(row.getStorageKey()).build());
        assertThat(object.contentLength()).isEqualTo(generated.length);
        assertThat(object.contentType()).isEqualTo("audio/mpeg");
        assertThat(storage.getBytes(row.getStorageKey())).as("audio read back from R2").isEqualTo(generated);

        long hitsBefore = row.getHitCount();
        started = System.nanoTime();
        TtsStreamChunk replayed = tts.streamWithTimestamps(TEXT, VOICE_ID, null, null, List.of(), id -> {}).block(Duration.ofSeconds(90));
        long secondMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(replayed).isEqualTo(first);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(repository.findById(row.getCacheKey()).orElseThrow().getHitCount()).isEqualTo(hitsBefore + 1));
        long objectsUnderKey = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(row.getStorageKey()).build()).keyCount();
        assertThat(objectsUnderKey).isEqualTo(1);

        say("bucket=" + bucket);
        say("r2_key=" + row.getStorageKey());
        say("audio_bytes=" + generated.length + " content_type=" + object.contentType());
        say("first_request_ms=" + firstMillis + " (ElevenLabs + store)  second_request_ms=" + secondMillis + " (from cache)");
        say("db_row=" + row.getCacheKey() + " hit_count_now=" + (hitsBefore + 1));
        say("listen_url=" + storage.getFileUrl(row.getStorageKey(), UrlStrategy.SIGNED));
    }

    private java.util.Optional<TtsAudioCacheEntry> findRow(int audioBytes) {
        return repository.findAll().stream()
                .filter(e -> e.getAudioBytes() == audioBytes && e.getVoiceId().equals(VOICE_ID) && e.getTextLength() == TEXT.length())
                .findFirst();
    }

    private static boolean isMp3(byte[] data) {
        if (data.length < 4) {
            return false;
        }
        boolean id3Tag = data[0] == 'I' && data[1] == 'D' && data[2] == '3';
        boolean frameSync = (data[0] & 0xFF) == 0xFF && (data[1] & 0xE0) == 0xE0;
        return id3Tag || frameSync;
    }
}
