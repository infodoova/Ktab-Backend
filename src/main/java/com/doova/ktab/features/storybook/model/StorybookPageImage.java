package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.illustration.VisualQaResponse;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "tbl_storybook_page_images")
@Getter
@Setter
public class StorybookPageImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_page_id", nullable = false)
    private StorybookPage page;

    @Column(name = "col_generation", nullable = false)
    private int generation;

    @Column(name = "col_image_key", nullable = false, columnDefinition = "TEXT")
    private String imageKey;

    /** Downscaled JPEG served to readers; null on images made before the copy existed. */
    @Column(name = "col_web_image_key", columnDefinition = "TEXT")
    private String webImageKey;

    @Column(name = "col_model", nullable = false, length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private PageImageStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_qa_result", columnDefinition = "JSONB")
    private VisualQaResponse qaResult;

    @Column(name = "col_cost_usd", nullable = false, precision = 10, scale = 4)
    private BigDecimal costUsd = BigDecimal.ZERO;
}
