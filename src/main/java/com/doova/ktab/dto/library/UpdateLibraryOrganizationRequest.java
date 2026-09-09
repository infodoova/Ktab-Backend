package com.doova.ktab.dto.library;

import jakarta.validation.constraints.Size;

public record UpdateLibraryOrganizationRequest(
        @Size(max = 255, message = "{validation.library.name.size}")
        String name,

        String description,
        String city,
        String country,
        String address,
        String website,
        String email,
        String phone,
        String status
) {}
