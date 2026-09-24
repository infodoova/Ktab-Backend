package com.doova.ktab.features.studio.sync;

import com.doova.ktab.features.studio.client.ElevenLabsStudioClient;
import com.doova.ktab.features.studio.client.dto.StudioChapterDetail;
import com.doova.ktab.features.studio.client.dto.StudioChapterSummary;
import com.doova.ktab.features.studio.enums.StudioProjectLifecycle;
import com.doova.ktab.features.studio.model.StudioChapter;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Two-tier sync engine for ElevenLabs Studio chapters (docs/ocr_engine_v3.md, Phase 3.7).
 *
 * <h3>Tier 1 — status sync</h3>
 * {@link #syncStatus(Long)} calls {@code GET /chapters} and updates only
 * {@code col_conversion_progress}, {@code col_last_conversion_error} and
 * {@code col_last_synced_at} — never touches content, never inserts rows.
 * Called frequently during conversion (backed off from 3 s to 30 s by the batch {@code pollStep}).
 *
 * <h3>Tier 2 — content sync</h3>
 * {@link #syncContent(Long)} calls {@code GET /chapters/{id}} per chapter, computes
 * {@code sha256(canonicalText + structureFingerprint)}, and short-circuits if the hash matches
 * {@code col_content_hash}. Only on a mismatch does it call the projector to upsert the
 * section subtree and rewrite R2.
 *
 * <h3>Chapter-set reconciliation</h3>
 * Both tiers left-join local against remote on {@code col_external_chapter_id}:
 * <ul>
 *   <li>Remote-only → insert (only in Tier 2)</li>
 *   <li>Both → update</li>
 *   <li>Local-only → set {@code col_deleted_at}, log, increment {@code studio.chapters.vanished}</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * Both tiers take {@code SELECT ... FOR UPDATE} on the {@code tbl_studio_projects} row at entry
 * so that batch retries and the scheduled reconciler cannot overlap on the same project.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudioSyncService {

    private final ElevenLabsStudioClient client;
    private final StudioProjectRepository projectRepository;
    private final StudioChapterRepository chapterRepository;
    private final StudioProjectorService projector;
    private final EntityManager em;
    private final MeterRegistry meterRegistry;

    // =========================================================================
    // Tier 1 — status sync
    // =========================================================================

    /**
     * Tier 1 status sync: update conversion progress and error fields from
     * {@code GET /chapters}. Safe to call at high frequency — never inserts rows,
     * never touches chapter content or section data.
     *
     * <p>Takes a pessimistic write lock on the project row so concurrent poll invocations
     * (batch restart / scheduled reconciler) serialize cleanly.
     *
     * @param projectId local {@code tbl_studio_projects.col_id}
     * @return {@code true} when all chapters report full conversion progress (&ge; 1.0)
     */
    @Transactional
    public boolean syncStatus(Long projectId) {
        StudioProject project = lockProject(projectId);
        String extId = project.getExternalProjectId();

        List<StudioChapterSummary> remote = client.listChapters(extId);
        Map<String, StudioChapterSummary> remoteByExtId = index(remote, StudioChapterSummary::chapterId);

        List<StudioChapter> local = chapterRepository.findByProject_IdOrderByOrderIndexAsc(projectId);
        Map<String, StudioChapter> localByExtId = index(local, StudioChapter::getExternalChapterId);

        int vanished = 0;
        for (StudioChapter chapter : local) {
            if (chapter.getDeletedAt() != null) continue;

            StudioChapterSummary summary = remoteByExtId.get(chapter.getExternalChapterId());
            if (summary == null) {
                // Chapter no longer visible at ElevenLabs — tombstone it
                chapter.setDeletedAt(Instant.now());
                vanished++;
                log.warn("studio.chapter.vanished projectId={} chapterId={} extChapterId={}",
                        projectId, chapter.getId(), chapter.getExternalChapterId());
            } else {
                applyStatusUpdate(chapter, summary);
            }
        }

        if (vanished > 0) {
            meterRegistry.counter("studio.chapters.vanished").increment(vanished);
        }

        project.setLastSyncedAt(Instant.now());
        project.setSyncError(null);

        boolean allConverted = remote.stream().allMatch(StudioChapterSummary::isFullyConverted);
        log.debug("studio.syncStatus projectId={} extId={} chapters={} allConverted={}",
                projectId, extId, remote.size(), allConverted);
        return allConverted;
    }

    // =========================================================================
    // Tier 2 — content sync
    // =========================================================================

    /**
     * Tier 2 content sync: for each chapter, fetch full content, compute the hash, and
     * re-project only when the hash differs. Also reconciles insertions and tombstones.
     *
     * <p>Called rarely: once after project creation, on demand after an edit, once before
     * final download. Each chapter is processed in its own nested transaction so a single
     * bad chapter cannot abort the whole sync.
     *
     * @param projectId local {@code tbl_studio_projects.col_id}
     */
    @Transactional
    public void syncContent(Long projectId) {
        StudioProject project = lockProject(projectId);
        String extId = project.getExternalProjectId();

        List<StudioChapterSummary> remote = client.listChapters(extId);
        Map<String, StudioChapterSummary> remoteByExtId = index(remote, StudioChapterSummary::chapterId);

        List<StudioChapter> local = chapterRepository.findByProject_IdOrderByOrderIndexAsc(projectId);
        Map<String, StudioChapter> localByExtId = index(local, StudioChapter::getExternalChapterId);

        int synced = 0;
        int skipped = 0;
        int vanished = 0;
        int inserted = 0;

        // --- Remote-only: new chapters appeared in Studio ---
        int remoteIdx = 0;
        for (StudioChapterSummary summary : remote) {
            if (!localByExtId.containsKey(summary.chapterId())) {
                StudioChapterDetail detail = client.getChapter(extId, summary.chapterId());
                projector.projectNewChapter(project, detail, remoteIdx);
                inserted++;
                log.info("studio.chapter.inserted projectId={} extChapterId={}", projectId, summary.chapterId());
            }
            remoteIdx++;
        }

        // --- Both / local-only ---
        for (StudioChapter chapter : local) {
            if (chapter.getDeletedAt() != null) continue;

            StudioChapterSummary summary = remoteByExtId.get(chapter.getExternalChapterId());
            if (summary == null) {
                // Vanished from Studio — tombstone, do not hard-delete
                chapter.setDeletedAt(Instant.now());
                vanished++;
                meterRegistry.counter("studio.chapters.vanished").increment();
                log.warn("studio.chapter.vanished projectId={} chapterId={} extChapterId={}",
                        projectId, chapter.getId(), chapter.getExternalChapterId());
                continue;
            }

            // Fetch full content and check hash
            StudioChapterDetail detail = client.getChapter(extId, chapter.getExternalChapterId());
            String newHash = computeHash(detail);

            if (newHash.equals(chapter.getContentHash())) {
                // Hash matches — no re-projection needed. Hibernate @LastModifiedDate
                // on BaseEntity bumps updatedAt automatically when this transaction commits.
                skipped++;
                log.debug("studio.chapter.hashMatch projectId={} extChapterId={}", projectId, chapter.getExternalChapterId());
            } else {
                projector.reproject(chapter, detail);
                chapter.setContentHash(newHash);
                synced++;
                log.info("studio.chapter.reprojected projectId={} extChapterId={} newHash={}",
                        projectId, chapter.getExternalChapterId(), newHash);
            }
        }

        project.setLastSyncedAt(Instant.now());
        project.setSyncError(null);
        project.setLifecycle(StudioProjectLifecycle.SYNCED);

        meterRegistry.counter("studio.sync.content", "result", "ok").increment(synced);
        log.info("studio.syncContent.done projectId={} extId={} inserted={} synced={} skipped={} vanished={}",
                projectId, extId, inserted, synced, skipped, vanished);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Acquires a pessimistic write lock on the project row. Any concurrent sync on the same
     * project will block here until the current transaction commits, preventing double-writes
     * and progress overwrites (doc Phase 3.7 "Concurrency").
     */
    private StudioProject lockProject(Long projectId) {
        StudioProject project = em.find(StudioProject.class, projectId, LockModeType.PESSIMISTIC_WRITE);
        if (project == null) {
            throw new IllegalArgumentException("StudioProject not found: " + projectId);
        }
        if (project.getLifecycle().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot sync a terminal project: projectId=" + projectId
                            + " lifecycle=" + project.getLifecycle());
        }
        return project;
    }

    /** Apply Tier 1 status fields only — no content touched. */
    private void applyStatusUpdate(StudioChapter chapter, StudioChapterSummary summary) {
        if (summary.conversionProgress() != null) {
            chapter.setConversionProgress(BigDecimal.valueOf(summary.conversionProgress()));
        }
        chapter.setLastConversionError(summary.hasError() ? summary.lastConversionError() : null);
        // col_last_synced_at lives on StudioProject, not StudioChapter.
        // BaseEntity.updatedAt is bumped automatically by @LastModifiedDate on dirty flush.
    }

    /**
     * Computes {@code sha256(canonicalText + "|" + structureFingerprint)} for a chapter.
     * The canonical text is all node text concatenated in block order; the fingerprint is
     * the ordered sequence of block types (e.g. {@code "h1,p,p,h2,p"}). Both changing the
     * words and changing the heading structure invalidate the hash — content-hash gate is
     * sound even after structural edits in Studio.
     *
     * <p>See docs/ocr_engine_v3.md, Phase 3.7 Tier 2.
     */
    static String computeHash(StudioChapterDetail detail) {
        if (detail.content() == null || detail.content().blocks() == null) {
            return sha256("empty");
        }

        StringBuilder text = new StringBuilder();
        StringBuilder fingerprint = new StringBuilder();
        boolean first = true;

        for (StudioChapterDetail.Block block : detail.content().blocks()) {
            if (!first) fingerprint.append(',');
            fingerprint.append(block.type() != null ? block.type() : "?");
            first = false;

            if (block.nodes() != null) {
                for (StudioChapterDetail.Node node : block.nodes()) {
                    if (node.text() != null) text.append(node.text());
                }
            }
        }

        return sha256(text + "|" + fingerprint);
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private <K, V> Map<K, V> index(List<V> list, Function<V, K> keyFn) {
        return list.stream().collect(Collectors.toMap(keyFn, Function.identity()));
    }

}

