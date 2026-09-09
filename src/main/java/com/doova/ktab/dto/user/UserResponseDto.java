package com.doova.ktab.dto.user;

import com.doova.ktab.model.user.User;

public record UserResponseDto(
        Long id,
        String email,
        String firstName,
        String middleName,
        String lastName,
        String fullName,
        String role,
        String active
) {
    public static UserResponseDto from(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getMiddleName(),
                user.getLastName(),
                user.getFullName(),
                user.getRole(),
                user.getActive()
        );
    }
}
