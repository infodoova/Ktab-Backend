package com.doova.ktab.features.earlyaccess.web.dto;

import com.doova.ktab.enums.user.UserRole;

/** Echoes back what was registered. No id: the table is not something a visitor can look anything up in. */
public record EarlyAccessSignupResponse(String email, String fullName, UserRole role, String organizationName,
                                        String plan, boolean earlyAccess) {
}
