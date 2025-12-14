package com.doova.ktab.api.dto.request;

public record VerifyCodeRequest(
        @jakarta.validation.constraints.Email @jakarta.validation.constraints.NotBlank String email,

        @jakarta.validation.constraints.NotBlank String code) {
}
