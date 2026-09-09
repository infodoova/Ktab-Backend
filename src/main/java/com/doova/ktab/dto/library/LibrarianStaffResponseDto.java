package com.doova.ktab.dto.library;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibrarianStaffResponseDto {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String role;
    private String roleCode;
    private String active;
    private Long libraryOrganizationId;
    private String libraryOrganizationName;
}
