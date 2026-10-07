package com.doova.ktab.features.earlyaccess.web.dto;

import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.features.earlyaccess.enums.EarlyAccessRoles;
import com.doova.ktab.features.earlyaccess.enums.Gender;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What a visitor sends to ask for early access. Whether early access is granted is never part of it. Every message is a
 * key of messages.properties.
 */
public record EarlyAccessSignupRequest(
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        @Size(max = 255, message = "{validation.early.access.email.size}") String email,
        @NotBlank(message = "{validation.early.access.full.name.required}")
        @Size(max = 200, message = "{validation.early.access.full.name.size}") String fullName,
        @NotNull(message = "{validation.role.required}") UserRole role,
        @Valid EarlyAccessOrganizationRequest organization,
        @Pattern(regexp = "^\\+?[0-9 ()\\-]{6,31}$", message = "{validation.early.access.phone.invalid}") String phoneNumber,
        Gender gender,
        @Pattern(regexp = "^[A-Za-z0-9_\\-]{1,50}$", message = "{validation.early.access.plan.invalid}") String plan
) {

    /** Early access is offered for readers, authors and admin librarians only. A missing role is reported by {@code @NotNull}. */
    @JsonIgnore
    @AssertTrue(message = "{validation.early.access.role.not.allowed}")
    public boolean isRoleAllowed() {
        return role == null || EarlyAccessRoles.ALLOWED.contains(role);
    }

    /** An admin librarian must give the organization they run, and nobody else has one. A missing role is reported by {@code @NotNull}. */
    @JsonIgnore
    @AssertTrue(message = "{validation.early.access.organization.inconsistent}")
    public boolean isOrganizationConsistent() {
        if (role == null) {
            return true;
        }
        return (role == UserRole.ADMIN_LIBRARIAN) == (organization != null);
    }
}
