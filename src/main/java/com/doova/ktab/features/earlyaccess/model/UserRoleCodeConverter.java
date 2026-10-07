package com.doova.ktab.features.earlyaccess.model;

import com.doova.ktab.enums.user.UserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a {@link UserRole} the way tbl_users does: as its code ("10", "20", "35"), not its name. */
@Converter
public class UserRoleCodeConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole role) {
        return role == null ? null : role.getCode();
    }

    @Override
    public UserRole convertToEntityAttribute(String code) {
        return UserRole.fromCode(code);
    }
}
