package com.doova.ktab.model.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.doova.ktab.model.base.BaseEntity;

@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "tbl_users", uniqueConstraints = {@UniqueConstraint(name = "uq_users_email", columnNames = "col_email")})
public class User extends BaseEntity {

    @NotBlank
    @Column(name = "col_email", nullable = false)
    private String email;

    @NotBlank
    @Column(name = "col_first_name", nullable = false)
    private String firstName;

    @Column(name = "col_middle_name")
    private String middleName;

    @NotBlank
    @Column(name = "col_last_name", nullable = false)
    private String lastName;

    @NotBlank
    @Column(name = "col_password_digest", nullable = false, length = 100)
    private String passwordDigest;

    @Transient
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotNull(message = "Role must not be Null")
    @Column(name = "col_role")
    private String role;

    @Column(name = "col_is_active")
    private String active;

    /**
     * Store BCrypt digest in passwordDigest; keep raw in transient password.
     */
    public void setPasswordAndDigest(String rawPassword, BCryptPasswordEncoder encoder) {
        this.password = rawPassword;
        this.passwordDigest = encoder.encode(rawPassword);
    }

    /**
     * Check raw password against stored digest.
     */
    public boolean isPassword(String rawPassword, BCryptPasswordEncoder encoder) {
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
}
