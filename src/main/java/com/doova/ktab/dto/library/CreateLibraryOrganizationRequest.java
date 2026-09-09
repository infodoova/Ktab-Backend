package com.doova.ktab.dto.library;

import jakarta.validation.constraints.NotBlank;
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
        String phone
) {}
