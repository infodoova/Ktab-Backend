package com.doova.ktab.dto.library;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLibraryOrganizationRequest(
        @NotBlank(message = "{validation.library.name.required}")
        @Size(max = 255, message = "{validation.library.name.size}")
        String name,

        String description,
        String city,
        String country,
        String address,
        String website,
        String email,
        String phone,

        @NotNull(message = "{validation.library.admin.required}")
        @Valid
        AssignLibrarianRequest admin
) {
    public CreateLibraryOrganizationRequest(
            String name,
            String description,
            String city,
            String country,
            String address,
            String website,
            String email,
            String phone
    ) {
        this(name, description, city, country, address, website, email, phone, null);
    }
}
