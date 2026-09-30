package com.doova.ktab.features.trailer.model;

import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tbl_book_trailers")
@Getter
@Setter
public class BookTrailer extends BaseEntity {

    @Column(name = "col_book_id", nullable = false)
    private Long bookId;

    @Column(name = "col_requested_by")
    private Long requestedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private TrailerStatus status = TrailerStatus.QUEUED;

    @Column(name = "col_session_id", length = 80)
    private String sessionId;

    @Column(name = "col_book_file_id", length = 80)
    private String bookFileId;

    @Column(name = "col_cover_file_id", length = 80)
    private String coverFileId;

    @Column(name = "col_agent_version")
    private Integer agentVersion;

    @Column(name = "col_outcome_result", length = 40)
    private String outcomeResult;

    @Column(name = "col_outcome_explanation", columnDefinition = "TEXT")
    private String outcomeExplanation;

    /** Higgsfield jobs actually created (a call is only counted once its result carries a job id). */
    @Column(name = "col_higgsfield_generations", nullable = false)
    private int higgsfieldGenerations;

    /** Every generate_video call, including ones rejected by concurrency limits or answered with a preset suggestion. */
    @Column(name = "col_higgsfield_calls", nullable = false)
    private int higgsfieldCalls;

    @Column(name = "col_video_key", length = 300)
    private String videoKey;

    @Column(name = "col_clean_video_key", length = 300)
    private String cleanVideoKey;

    @Column(name = "col_captions_key", length = 300)
    private String captionsKey;

    @Column(name = "col_qc_report", columnDefinition = "TEXT")
    private String qcReportJson;

    @Column(name = "col_error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "col_next_check_at", nullable = false)
    private Instant nextCheckAt = Instant.now();

    @Column(name = "col_started_at")
    private Instant startedAt;

    @Column(name = "col_finished_at")
    private Instant finishedAt;
}
