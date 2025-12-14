package com.doova.doovafeeds.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;


public record UserRegisterRequest(
        @NotBlank String firstName,
        String middleName,
        @NotBlank String lastName,
        @Email @NotBlank String email,
        @NotBlank String password
) {
    @Override
    public String firstName() {
        return firstName;
    }

    @Override
    public String middleName() {
        return middleName;
    }

    @Override
    public String lastName() {
        return lastName;
    }

    @Override
    public String email() {
        return email;
    }

    @Override
    public String password() {
        return password;
    }
}

