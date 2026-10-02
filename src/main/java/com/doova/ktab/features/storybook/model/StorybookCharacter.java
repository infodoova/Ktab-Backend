package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.CharacterSheetStatus;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "tbl_storybook_characters")
@Getter
@Setter
public class StorybookCharacter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_storybook_id", nullable = false)
    private Storybook storybook;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_kind", nullable = false, length = 20)
    private CharacterKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_attributes", nullable = false, columnDefinition = "JSONB")
    private CharacterAttributes attributes;

    @Column(name = "col_sheet_key", columnDefinition = "TEXT")
    private String sheetKey;

    @Column(name = "col_sheet_version", nullable = false)
    private int sheetVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_sheet_status", nullable = false, length = 20)
    private CharacterSheetStatus sheetStatus = CharacterSheetStatus.NOT_STARTED;

    @Column(name = "col_photo_key", columnDefinition = "TEXT")
    private String photoKey;

    @Column(name = "col_photo_consent_at")
    private Instant photoConsentAt;

    @Column(name = "col_photo_purged_at")
    private Instant photoPurgedAt;

    @Column(name = "col_character_id", length = 64)
    private String characterId;

    @Column(name = "col_character_type", length = 32, nullable = false)
    private String characterType = "HUMAN";

    @Column(name = "col_role", length = 32, nullable = false)
    private String role = "MAIN";

    @Column(name = "col_relationship", length = 128)
    private String relationship;

    @Column(name = "col_clothing", columnDefinition = "TEXT")
    private String clothing;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_personality", columnDefinition = "JSONB")
    private java.util.List<String> personality;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_advanced_details", columnDefinition = "JSONB")
    private java.util.Map<String, Object> advancedDetails;

    @Column(name = "col_master_sheet_key", columnDefinition = "TEXT")
    private String masterSheetKey;
}
