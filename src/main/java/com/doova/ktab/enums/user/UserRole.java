package com.doova.ktab.enums.user;

import lombok.Getter;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@Getter
public enum UserRole {
    ADMIN("00"), AUTHOR("10"), READER("20"), LIBRARIAN("30"), ADMIN_LIBRARIAN("35");

    private final String code;

    UserRole(String code) {
        this.code = code;
    }

    public static List<String> userRoleList() {
        String[] userRoles = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().map(role -> role.getAuthority()).toArray(String[]::new);

        return List.of(userRoles);
    }

    public static UserRole fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (UserRole role : values()) {
            if (role.code.equalsIgnoreCase(code) || role.name().equalsIgnoreCase(code)) {
                return role;
            }
        }
        return null;
    }
}

