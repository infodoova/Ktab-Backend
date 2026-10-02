package com.doova.ktab.features.trailer.model;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tbl_trailer_oauth_states")
@Getter
@Setter
public class TrailerOAuthState extends BaseEntity {

    @Column(name = "col_state", nullable = false, length = 100)
    private String state;

    @Column(name = "col_code_verifier", nullable = false, length = 200)
    private String codeVerifier;

    @Column(name = "col_client_id", nullable = false, length = 200)
    private String clientId;

    @Column(name = "col_redirect_uri", nullable = false, length = 500)
    private String redirectUri;

    @Column(name = "col_admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "col_used", nullable = false)
    private boolean used;
}
