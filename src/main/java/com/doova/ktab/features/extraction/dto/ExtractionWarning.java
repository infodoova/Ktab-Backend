package com.doova.ktab.features.extraction.dto;

public record ExtractionWarning(WarningCode code, Severity severity, String message) {
}
