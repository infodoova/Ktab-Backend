package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tbl_storybooks")
@Getter
@Setter
public class Storybook extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_owner_user_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_child_profile_id", nullable = false)
    private ChildProfile childProfile;

    @Column(name = "col_blueprint_key", nullable = false, length = 80)
    private String blueprintKey;

    @Column(name = "col_blueprint_version", nullable = false)
    private int blueprintVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_inputs", nullable = false, columnDefinition = "JSONB")
    private StoryInputs inputs;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_style", nullable = false, length = 40)
    private ArtStyle style;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_language_variety", nullable = false, length = 20)
    private LanguageVariety variety;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_tashkeel_level", nullable = false, length = 10)
    private TashkeelLevel tashkeelLevel;

    @Column(name = "col_page_count", nullable = false)
    private short pageCount;

    public void setPageCount(int pageCount) {
        this.pageCount = (short) pageCount;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private StorybookStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_failed_from_status", length = 20)
    private StorybookStatus failedFromStatus;

    @Column(name = "col_failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "col_title_ar", length = 200)
    private String titleAr;

    @Column(name = "col_cover_scene_en", columnDefinition = "TEXT")
    private String coverSceneEn;

    @Column(name = "col_dedication", length = 300)
    private String dedication;

    @Column(name = "col_story_approved_at")
    private Instant storyApprovedAt;

    @Column(name = "col_look_approved_at")
    private Instant lookApprovedAt;

    @Column(name = "col_pdf_key", columnDefinition = "TEXT")
    private String pdfKey;

    @Column(name = "col_look_regenerations", nullable = false)
    private int lookRegenerations;

    @Column(name = "col_page_regenerations", nullable = false)
    private int pageRegenerations;

    @Column(name = "col_total_cost_usd", nullable = false, precision = 10, scale = 4)
    private BigDecimal totalCostUsd = BigDecimal.ZERO;

    @Column(name = "col_theme", length = 128)
    private String theme;

    @Column(name = "col_story_tone", length = 64)
    private String storyTone;

    @Column(name = "col_lesson", length = 128)
    private String lesson;

    @Column(name = "col_story_idea", columnDefinition = "TEXT")
    private String storyIdea;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_things_to_avoid", columnDefinition = "JSONB")
    private java.util.List<String> thingsToAvoid;

    @Column(name = "col_orientation", length = 32, nullable = false)
    private String orientation = "PORTRAIT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_character_bible", columnDefinition = "JSONB")
    private String characterBible;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_story_blueprint", columnDefinition = "JSONB")
    private String storyBlueprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_style_bible", columnDefinition = "JSONB")
    private String styleBible;

    @Column(name = "col_language_ruleset", length = 64)
    private String languageRuleset;
}
