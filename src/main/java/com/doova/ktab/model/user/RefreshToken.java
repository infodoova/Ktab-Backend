package com.doova.ktab.model.user;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tbl_refresh_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uq_refresh_tokens_token", columnNames = "col_token")
})
public class RefreshToken extends BaseEntity {

    @NotBlank
    @Column(name = "col_token", nullable = false, length = 500, unique = true)
    private String token;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_user_id", nullable = false)
    private User user;

    @NotNull
    @Column(name = "col_expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "col_revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "col_replaced_by_token", length = 500)
    private String replacedByToken;

    @Column(name = "col_device_info")
    private String deviceInfo;

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isActive() {
        return !revoked && !isExpired();
    }
}
