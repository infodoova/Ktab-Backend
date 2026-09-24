package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tbl_storybook_child_profiles")
@Getter
@Setter
public class ChildProfile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_owner_user_id", nullable = false)
    private User owner;

    @Column(name = "col_name_ar", nullable = false, length = 40)
    private String nameAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_gender", nullable = false, length = 10)
    private ChildGender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_age_band", nullable = false, length = 10)
    private AgeBand ageBand;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_appearance", nullable = false, columnDefinition = "JSONB")
    private ChildAppearance appearance;
}
