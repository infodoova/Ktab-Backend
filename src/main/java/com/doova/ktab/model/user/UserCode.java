package com.doova.ktab.model.user;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tbl_user_codes")
public class UserCode extends BaseEntity {

    // The code that is sent to the user (email / SMS)
    @Column(name = "col_code", nullable = false)
    private String code;

    // Code type: EMAIL_VERIFY, RESET_PASSWORD, etc.
    @NotNull
    @Column(name = "col_code_type", nullable = false)
    private String codeType;

    // When the code expires
    @NotNull
    @Column(name = "col_expires_at", nullable = false)
    private Instant expiresAt;

    // Whether the code has been used
    @Builder.Default
    @Column(name = "col_is_used")
    private boolean used = false;

    // Relation with User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_user_id", nullable = false)
    private User user;

    // Helpers
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}

