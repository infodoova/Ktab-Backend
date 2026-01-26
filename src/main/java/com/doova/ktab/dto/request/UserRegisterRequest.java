package com.doova.ktab.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;


public record UserRegisterRequest(@NotBlank String firstName, String middleName, @NotBlank String lastName,
                                  @Email @NotBlank String email, @NotBlank String password, @NotBlank String role) {
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

    @Override
    public String role() {
        return role;
    }
}

