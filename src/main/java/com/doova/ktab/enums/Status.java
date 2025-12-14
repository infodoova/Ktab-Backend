package com.doova.ktab.enums;

public enum Status {
    INACTIVE("0"), ACTIVE("1");

    private final String code;

    Status(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static Status fromCode(String code) {
        return switch (code) {
            case "0" -> INACTIVE;
            case "1" -> ACTIVE;
            default -> throw new IllegalArgumentException("Unknown code: " + code);
        };
    }
}
