package com.doova.doovafeeds.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(@Email @NotBlank(message = "Email cannot be empty") String email,
                               @NotBlank(message = "Password cannot be empty") String password) {
    @Override
    public String email() {
        return email;
    }

    @Override
    public String password() {
        return password;
    }
}
