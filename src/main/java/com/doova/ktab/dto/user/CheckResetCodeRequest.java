package com.doova.ktab.dto.user;

import jakarta.validation.constraints.NotBlank;

public record CheckResetCodeRequest(@NotBlank(message = "{validation.email.required}") String email,

                                    @NotBlank(message = "{validation.code.required}") String code) {
}
