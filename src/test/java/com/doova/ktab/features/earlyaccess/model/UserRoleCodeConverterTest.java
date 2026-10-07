package com.doova.ktab.features.earlyaccess.model;

import com.doova.ktab.enums.user.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleCodeConverterTest {

    private final UserRoleCodeConverter converter = new UserRoleCodeConverter();

    @Test
    void aRoleIsStoredAsTheSameCodeTblUsersUses() {
        assertThat(converter.convertToDatabaseColumn(UserRole.READER)).isEqualTo("20");
        assertThat(converter.convertToDatabaseColumn(UserRole.AUTHOR)).isEqualTo("10");
        assertThat(converter.convertToDatabaseColumn(UserRole.ADMIN_LIBRARIAN)).isEqualTo("35");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void aStoredCodeComesBackAsTheRoleAndNothingIsLostOnTheWay() {
        for (UserRole role : UserRole.values()) {
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(role))).isEqualTo(role);
        }
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
