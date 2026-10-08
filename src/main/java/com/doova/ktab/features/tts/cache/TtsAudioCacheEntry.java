package com.doova.ktab.features.tts.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Index row for one cached reader narration. The MP3 bytes live in object storage under {@link #storageKey};
 * the word timings are kept here so a cache hit can rebuild the exact response ElevenLabs would have returned.
 */
@Entity
@Table(
        name = "tbl_tts_audio_cache",
        indexes = @Index(name = "idx_tts_audio_cache_last_accessed", columnList = "col_last_accessed_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TtsAudioCacheEntry {

    /** SHA-256 hex of everything that influences the generated audio. */
    @Id
    @Column(name = "col_cache_key", nullable = false, length = 64)
    private String cacheKey;

    @Column(name = "col_storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "col_alignment_json", nullable = false, columnDefinition = "TEXT")
    private String alignmentJson;

    @Column(name = "col_voice_id", nullable = false, length = 128)
    private String voiceId;

    @Column(name = "col_model_id", nullable = false, length = 64)
    private String modelId;

    @Column(name = "col_text_length", nullable = false)
    private int textLength;

    @Column(name = "col_audio_bytes", nullable = false)
    private long audioBytes;

    @Column(name = "col_hit_count", nullable = false)
    private long hitCount;

    @Column(name = "col_created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "col_last_accessed_at", nullable = false)
    private Instant lastAccessedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (lastAccessedAt == null) lastAccessedAt = now;
    }
}
