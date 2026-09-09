package com.doova.ktab.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(@Email(message = "{validation.email.invalid}") @NotBlank(message = "{validation.email.required}") String email,
                               @NotBlank(message = "{validation.password.required}") String password) {
    @Override
    public String email() {
        return email;
    }

    @Override
    public String password() {
        return password;
    }
}
