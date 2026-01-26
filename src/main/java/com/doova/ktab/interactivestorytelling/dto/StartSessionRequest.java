package com.doova.ktab.interactivestorytelling.dto;

import jakarta.validation.constraints.NotBlank;

public record StartSessionRequest(@NotBlank String readerId) {}
