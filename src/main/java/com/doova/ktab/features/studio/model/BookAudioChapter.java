package com.doova.ktab.features.studio.model;

import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookSection;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Durable, vendor-neutral audiobook chapter — the product. Readers only ever query this
 * table: no {@code externalProjectId}/{@code externalChapterId} here, so the vendor never
 * leaks into the read path. After {@link StudioProject} cleanup, every ingestion-state row
 * for a book can be dropped and every audiobook keeps working.
 * See docs/ocr_engine_v3.md, Phase 3.2.
 */
@Entity
@Table(
        name = "tbl_book_audio_chapters",
        uniqueConstraints = @UniqueConstraint(name = "uq_book_audio_chapters_sort",
                columnNames = {"col_book_id", "col_sort_order"})
)
@Getter
@Setter
public class BookAudioChapter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_book_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_book_section_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BookSection bookSection;

    @Column(name = "col_sort_order", nullable = false)
    private int sortOrder;

    /** R2: audio/{bookId}/chapters/ch-{sortOrder}.mp3 */
    @Column(name = "col_audio_path", nullable = false)
    private String audioPath;

    /** R2: gzipped {pageId,charStart,charEnd,startMs,endMs}[] */
    @Column(name = "col_timings_path")
    private String timingsPath;

    @Column(name = "col_duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "col_size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "col_sha256", nullable = false, length = 64)
    private String sha256;
}
