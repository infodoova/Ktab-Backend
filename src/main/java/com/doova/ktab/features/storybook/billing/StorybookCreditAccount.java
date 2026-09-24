package com.doova.ktab.features.storybook.billing;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tbl_storybook_credit_accounts")
@Getter
@Setter
public class StorybookCreditAccount extends BaseEntity {

    @Column(name = "col_user_id", nullable = false)
    private Long userId;

    @Column(name = "col_balance", nullable = false)
    private int balance;
}
