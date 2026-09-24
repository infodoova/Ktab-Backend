package com.doova.ktab.features.studio.model;

import com.doova.ktab.features.studio.enums.StudioProjectLifecycle;
import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.book.Book;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Ingestion-state scaffolding for one ElevenLabs Studio project backing a book. Purgeable:
 * after {@code cleanupStep} confirms deletion (or a purge archives it early), this row and
 * its chapters can be dropped entirely without affecting {@link BookAudioChapter} rows, which
 * are the durable product. See docs/ocr_engine_v3.md, Phase 3.1.
 */
@Entity
@Table(
        name = "tbl_studio_projects",
        uniqueConstraints = @UniqueConstraint(name = "uq_studio_projects_external_id", columnNames = "col_external_project_id")
)
@Getter
@Setter
public class StudioProject extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_book_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Book book;

    @Column(name = "col_external_project_id", nullable = false, length = 64)
    private String externalProjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_lifecycle", nullable = false, length = 30)
    @ColumnDefault("'PENDING'")
    private StudioProjectLifecycle lifecycle = StudioProjectLifecycle.PENDING;

    /** NULL means the remote project may still exist at ElevenLabs - the orphan reconciler's signal. */
    @Column(name = "col_project_deleted_at")
    private Instant projectDeletedAt;

    @Column(name = "col_model_id", length = 100)
    private String modelId;

    @Column(name = "col_title_voice_id", length = 64)
    private String titleVoiceId;

    @Column(name = "col_paragraph_voice_id", length = 64)
    private String paragraphVoiceId;

    @Column(name = "col_quality_preset", length = 30)
    private String qualityPreset;

    /** [{id, versionId}] - reproducibility record, not a normalized dictionary catalogue. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_pronunciation_dicts", columnDefinition = "JSONB")
    private String pronunciationDicts;

    /** R2 key for the raw chapter JSON dump, so a projection can be replayed without re-calling the API. */
    @Column(name = "col_raw_content_path")
    private String rawContentPath;

    @Column(name = "col_last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "col_sync_error", columnDefinition = "TEXT")
    private String syncError;
}
