package com.doova.ktab.features.earlyaccess.enums;

import com.doova.ktab.enums.user.UserRole;

import java.util.Set;

/** The user roles early access is offered for. Nobody can ask for early access as an admin, publisher or plain librarian. */
public final class EarlyAccessRoles {

    public static final Set<UserRole> ALLOWED = Set.of(UserRole.READER, UserRole.AUTHOR, UserRole.ADMIN_LIBRARIAN);

    private EarlyAccessRoles() {
    }
}
