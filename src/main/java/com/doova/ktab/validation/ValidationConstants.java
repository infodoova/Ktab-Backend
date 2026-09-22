package com.doova.ktab.validation;

/**
 * Common validation constants used across DTOs and entities.
 */
public final class ValidationConstants {

    private ValidationConstants() {}

    /**
     * Unified production password policy regex:
     * - Minimum 8 characters: .{8,}
     * - At least one lowercase letter: (?=.*[a-z])
     * - At least one uppercase letter: (?=.*[A-Z])
     * - At least one digit: (?=.*\d)
     * - At least one special symbol / non-alphanumeric: (?=.*[^A-Za-z0-9\s])
     */
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}$";
}
