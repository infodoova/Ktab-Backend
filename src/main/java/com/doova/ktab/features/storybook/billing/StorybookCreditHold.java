package com.doova.ktab.features.storybook.billing;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tbl_storybook_credit_holds")
@Getter
@Setter
public class StorybookCreditHold extends BaseEntity {

    @Column(name = "col_storybook_id", nullable = false)
    private Long storybookId;

    @Column(name = "col_user_id", nullable = false)
    private Long userId;

    @Column(name = "col_units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private CreditHoldStatus status;
}
