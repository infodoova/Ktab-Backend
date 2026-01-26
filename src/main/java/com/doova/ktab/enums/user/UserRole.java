package com.doova.ktab.enums.user;

import lombok.Getter;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@Getter
public enum UserRole {
    ADMIN("00"), AUTHOR("10"), READER("20");

    private final String code;

    UserRole(String code) {
        this.code = code;
    }

    public static List<String> userRoleList() {
        String[] userRoles = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().map(role -> role.getAuthority()).toArray(String[]::new);

        return List.of(userRoles);
    }

    public static UserRole fromCode(String code) {
        switch (code) {
            case "00":
                return ADMIN;
            case "10":
                return AUTHOR;
            case "20":
                return READER;
            default:
                throw new IllegalArgumentException("Unknown code: " + code);
        }
    }
}

