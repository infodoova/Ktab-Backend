package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_storybook_pages")
@Getter
@Setter
public class StorybookPage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_storybook_id", nullable = false)
    private Storybook storybook;

    /** 0 = cover, 1..N = story pages. */
    @Column(name = "col_page_index", nullable = false)
    private short pageIndex;

    public void setPageIndex(int pageIndex) {
        this.pageIndex = (short) pageIndex;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "col_kind", nullable = false, length = 10)
    private PageKind kind;

    @Column(name = "col_text_ar", columnDefinition = "TEXT")
    private String textAr;

    @Column(name = "col_scene_en", nullable = false, columnDefinition = "TEXT")
    private String sceneEn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_characters", nullable = false, columnDefinition = "JSONB")
    private List<CharacterInScene> characters = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "col_text_zone", nullable = false, length = 10)
    private TextZone textZone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_critic_problems", columnDefinition = "JSONB")
    private List<String> criticProblems;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_current_image_id")
    private StorybookPageImage currentImage;

    /** Latest image generation requested for this page (1-based; 0 = none yet). */
    @Column(name = "col_generation", nullable = false)
    private int generation;

    /** First generation of the current round; QA retries and model choice count from here. */
    @Column(name = "col_round_start_generation", nullable = false)
    private int roundStartGeneration = 1;
}
