package com.doova.ktab.api.dto;

import jakarta.validation.constraints.NotBlank;

public class CheckResetCodeRequest {

    @NotBlank
    private String email;

    @NotBlank
    private String code;

    // Getters and Setters
}
