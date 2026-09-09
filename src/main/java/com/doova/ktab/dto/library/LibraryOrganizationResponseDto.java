package com.doova.ktab.dto.library;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibraryOrganizationResponseDto {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String logoUrl;
    private String city;
    private String country;
    private String address;
    private String website;
    private String email;
    private String phone;
    private String status;
    private long totalBooks;
    private long totalStaff;
    private java.time.LocalDateTime createdAt;
}
