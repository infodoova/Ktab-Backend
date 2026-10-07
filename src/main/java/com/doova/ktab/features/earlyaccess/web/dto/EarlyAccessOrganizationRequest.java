package com.doova.ktab.features.earlyaccess.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The library organization an admin librarian signs up with. The limits are those of tbl_library_organizations, so
 * whatever is accepted here can be stored there later. Every message is a key of messages.properties.
 */
public record EarlyAccessOrganizationRequest(
        @NotBlank(message = "{validation.library.name.required}")
        @Size(max = 255, message = "{validation.library.name.size}") String name,
        @Size(max = 5000, message = "{validation.early.access.organization.description.size}") String description,
        @Size(max = 100, message = "{validation.early.access.organization.city.size}") String city,
        @Size(max = 100, message = "{validation.early.access.organization.country.size}") String country,
        @Size(max = 255, message = "{validation.early.access.organization.address.size}") String address,
        @Size(max = 255, message = "{validation.early.access.organization.website.size}")
        @Pattern(regexp = "^https?://\\S+$", message = "{validation.early.access.organization.website.invalid}") String website,
        @Email(message = "{validation.email.invalid}")
        @Size(max = 255, message = "{validation.early.access.email.size}") String email,
        @Pattern(regexp = "^\\+?[0-9 ()\\-]{6,31}$", message = "{validation.early.access.phone.invalid}") String phone
) {
}
