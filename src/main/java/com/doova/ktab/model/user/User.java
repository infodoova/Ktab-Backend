package com.doova.ktab.model.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.doova.ktab.model.base.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "tbl_users", uniqueConstraints = {
        @UniqueConstraint(name = "uq_users_email", columnNames = "col_email") })
public class User extends BaseEntity {

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Column(name = "col_email", nullable = false, unique = true)
    private String email;

    @NotBlank(message = "{validation.first_name.required}")
    @Column(name = "col_first_name", nullable = false)
    private String firstName;

    @Column(name = "col_middle_name")
    private String middleName;

    @Column(name = "col_last_name")
    private String lastName;

    @NotBlank(message = "{validation.password.required}")
    @Column(name = "col_password_digest", nullable = false, length = 100)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordDigest;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @NotNull(message = "{validation.role.required}")
    @Column(name = "col_role")
    private String role;

    @Column(name = "col_is_active")
    private String active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_library_organization_id")
    private com.doova.ktab.model.library.LibraryOrganization libraryOrganization;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserSettings settings;

    public void setSettings(UserSettings settings) {
        this.settings = settings;
        if (settings != null) {
            settings.setUser(this);
        }
    }

    /**
     * Store encoded password in passwordDigest; keep raw in transient password
     * field.
     */
    public void setPasswordAndDigest(String rawPassword, PasswordEncoder encoder) {
        this.password = rawPassword;
        this.passwordDigest = encoder.encode(rawPassword);
    }

    /**
     * Check raw password against stored digest.
     */
    public boolean isPassword(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, this.passwordDigest);
    }

    public String getFullName() {
        StringBuilder fullName = new StringBuilder();
        fullName.append(this.firstName);
        if (this.middleName != null && !this.middleName.isEmpty()) {
            fullName.append(" ").append(this.middleName);
        }
        fullName.append(" ").append(this.lastName);
        return fullName.toString();
    }

    @PrePersist
    @PreUpdate
    private void normalizeEmail() {
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase();
        }
    }
}
