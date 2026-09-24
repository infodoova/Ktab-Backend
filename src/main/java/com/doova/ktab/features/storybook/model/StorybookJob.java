package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.JobStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tbl_storybook_jobs")
@Getter
@Setter
public class StorybookJob extends BaseEntity {

    /** Plain id, not a relation: the claimer and worker never need the book row. */
    @Column(name = "col_storybook_id", nullable = false)
    private Long storybookId;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_step", nullable = false, length = 30)
    private JobStep step;

    /** -1 for book-level steps. */
    @Column(name = "col_page_index", nullable = false)
    private short pageIndex = -1;

    public void setPageIndex(int pageIndex) {
        this.pageIndex = (short) pageIndex;
    }

    @Column(name = "col_generation", nullable = false)
    private int generation;

    @Column(name = "col_idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "col_attempts", nullable = false)
    private int attempts;

    @Column(name = "col_next_run_at", nullable = false)
    private Instant nextRunAt = Instant.now();

    @Column(name = "col_locked_by", length = 100)
    private String lockedBy;

    @Column(name = "col_locked_at")
    private Instant lockedAt;

    @Column(name = "col_last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "col_finished_at")
    private Instant finishedAt;
}
