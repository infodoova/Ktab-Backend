package com.doova.doovafeeds.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(exclude = {"subscriptions", "audit"})
@Entity
@Table(
        name = "tbl_users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_email", columnNames = "col_email")
        }
)
public class User extends BaseEntity {

    // Core fields
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

    // Transient password input (validates min length, not persisted)
    @Transient
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @Column(name = "col_is_active")
    private boolean active;

    @OneToMany(mappedBy = "subscriber", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Subscription> subscriptions;
    // === Password helpers ===

    /** Store BCrypt digest in passwordDigest; keep raw in transient password. */
    public void setPasswordAndDigest(String rawPassword, BCryptPasswordEncoder encoder) {
        this.password = rawPassword;
        this.passwordDigest = encoder.encode(rawPassword);
    }

    /** Check raw password against stored digest. */
    public boolean isPassword(String rawPassword, BCryptPasswordEncoder encoder) {
        return encoder.matches(rawPassword, this.passwordDigest);
    }
}
