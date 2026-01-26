package com.doova.ktab.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CheckResetCodeRequest(@NotBlank(message = "Email is required") String email,

                                    @NotBlank(message = "Code is required") String code) {
}
