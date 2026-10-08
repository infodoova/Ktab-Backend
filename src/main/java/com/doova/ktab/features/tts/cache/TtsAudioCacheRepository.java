package com.doova.ktab.features.tts.cache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface TtsAudioCacheRepository extends JpaRepository<TtsAudioCacheEntry, String> {

    /** Records a cache hit without loading or versioning the row. */
    @Modifying
    @Transactional
    @Query("update TtsAudioCacheEntry e set e.hitCount = e.hitCount + 1, e.lastAccessedAt = :now where e.cacheKey = :key")
    int touch(@Param("key") String key, @Param("now") Instant now);
}
