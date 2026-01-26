package com.doova.ktab.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationCodeRequest(

        @NotBlank @Email String email

) {
}
