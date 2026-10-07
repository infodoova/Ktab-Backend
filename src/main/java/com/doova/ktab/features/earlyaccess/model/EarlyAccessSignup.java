package com.doova.ktab.features.earlyaccess.model;

import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.features.earlyaccess.enums.Gender;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Locale;

/**
 * Someone who asked for early access. Kept apart from the user and authentication tables on purpose: it carries contact
 * details and plan interest only, never a password, and has no part in logging in.
 */
@Entity
@Table(name = "tbl_early_access_signups")
@Getter
@Setter
public class EarlyAccessSignup extends BaseEntity {

    /** Stored trimmed and lower-case, so the unique index on the address cannot be dodged by capitalization. */
    @Column(name = "col_email", nullable = false, length = 255)
    private String email;

    @Column(name = "col_full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "col_phone_number", length = 32)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_gender", length = 10)
    private Gender gender;

    /** The role this person wants early access as: the same value tbl_users.col_role holds, so a signup can become a user as it is. */
    @Convert(converter = UserRoleCodeConverter.class)
    @Column(name = "col_role", nullable = false, length = 20)
    private UserRole role;

    /** Only for an admin librarian: the library organization they run. Null for everyone else. */
    @Embedded
    private EarlyAccessOrganization organization;

    /** True once early access has been granted to this person. */
    @Column(name = "col_early_access", nullable = false)
    private boolean earlyAccess;

    /** The plan this person asked for or was given, as a plan key. */
    @Column(name = "col_plan", length = 50)
    private String plan;

    @PrePersist
    @PreUpdate
    void normalize() {
        if (email != null) {
            email = email.strip().toLowerCase(Locale.ROOT);
        }
        if (fullName != null) {
            fullName = fullName.strip();
        }
        if (organization != null) {
            organization.normalize();
        }
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.strip();
            if (phoneNumber.isEmpty()) {
                phoneNumber = null;
            }
        }
    }
}
