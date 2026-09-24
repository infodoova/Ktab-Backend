package com.doova.ktab.features.storybook.model;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "tbl_storybook_ai_calls")
@Getter
@Setter
public class StorybookAiCall extends BaseEntity {

    @Column(name = "col_storybook_id")
    private Long storybookId;

    @Column(name = "col_job_id")
    private Long jobId;

    @Column(name = "col_purpose", nullable = false, length = 40)
    private String purpose;

    @Column(name = "col_provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "col_model", nullable = false, length = 100)
    private String model;

    @Column(name = "col_input_tokens")
    private Long inputTokens;

    @Column(name = "col_output_tokens")
    private Long outputTokens;

    @Column(name = "col_images", nullable = false)
    private int images;

    @Column(name = "col_cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "col_latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "col_success", nullable = false)
    private boolean success;

    @Column(name = "col_error", columnDefinition = "TEXT")
    private String error;
}
