package com.doova.ktab.features.studio.model;

import com.doova.ktab.features.studio.enums.StudioChapterOrigin;
import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.book.BookSection;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One Studio chapter, always linked to exactly one {@link BookSection} at creation time —
 * {@code bookSection} is {@code optional = false}. This is "Ktab never infers correspondence"
 * enforced by the schema: a chapter row cannot exist without a known section.
 * See docs/ocr_engine_v3.md, Phase 3.2.
 */
@Entity
@Table(
        name = "tbl_studio_chapters",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_studio_chapters_external",
                        columnNames = {"col_project_id", "col_external_chapter_id"}),
                @UniqueConstraint(name = "uq_studio_chapters_order",
                        columnNames = {"col_project_id", "col_order_index"})
        }
)
@Getter
@Setter
public class StudioChapter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private StudioProject project;

    @Column(name = "col_external_chapter_id", nullable = false, length = 64)
    private String externalChapterId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_book_section_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BookSection bookSection;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_origin", nullable = false, length = 20)
    private StudioChapterOrigin origin;

    @Column(name = "col_order_index", nullable = false)
    private int orderIndex;

    /** sha256(canonicalText + structureFingerprint); lets Tier-2 content sync no-op on a match. */
    @Column(name = "col_content_hash", length = 64)
    private String contentHash;

    @Column(name = "col_conversion_progress", precision = 4, scale = 3)
    private BigDecimal conversionProgress;

    @Column(name = "col_last_conversion_error", columnDefinition = "TEXT")
    private String lastConversionError;

    /** Tombstone; never hard-delete - the chapter's audio may already be live for readers. */
    @Column(name = "col_deleted_at")
    private Instant deletedAt;
}
