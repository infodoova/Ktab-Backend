package com.doova.ktab.features.studio.enums;

/**
 * Our own authoritative lifecycle for a {@code tbl_studio_projects} row. Deliberately
 * distinct from ElevenLabs Studio's own project "state" field, which is opaque, can
 * change under us, and is mirrored read-only if at all — never branched on.
 * See docs/ocr_engine_v3.md, Phase 3.1.
 */
public enum StudioProjectLifecycle {
    PENDING,
    CREATED,
    SYNCED,
    CONVERTING,
    CONVERTED,
    /** Terminal: closed out (e.g. by a purge/reroute) without confirmation the remote project was deleted. */
    ARCHIVED,
    FAILED,
    /** Terminal: remote project confirmed deleted; {@code col_project_deleted_at} is set. */
    DELETED;

    public boolean isTerminal() {
        return this == ARCHIVED || this == FAILED || this == DELETED;
    }
}
