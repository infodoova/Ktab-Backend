package com.doova.ktab.model.user;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("User entity should have unique constraint on col_email at table and column level")
    void userEntity_hasUniqueConstraintOnEmail() throws NoSuchFieldException {
        Table tableAnn = User.class.getAnnotation(Table.class);
        assertThat(tableAnn).isNotNull();
        assertThat(tableAnn.name()).isEqualTo("tbl_users");

        boolean hasEmailConstraint = Arrays.stream(tableAnn.uniqueConstraints())
                .anyMatch(uc -> "uq_users_email".equals(uc.name())
                        && Arrays.asList(uc.columnNames()).contains("col_email"));
        assertThat(hasEmailConstraint).isTrue();

        Field emailField = User.class.getDeclaredField("email");
        Column columnAnn = emailField.getAnnotation(Column.class);
        assertThat(columnAnn).isNotNull();
        assertThat(columnAnn.name()).isEqualTo("col_email");
        assertThat(columnAnn.unique()).isTrue();

        Email emailAnn = emailField.getAnnotation(Email.class);
        assertThat(emailAnn).isNotNull();
    }

    @Test
    @DisplayName("normalizeEmail should trim and lowercase email")
    void normalizeEmail_trimsAndLowercasesEmail() throws Exception {
        User user = User.builder()
                .email("  Test.User@KTab.Org  ")
                .firstName("Test")
                .lastName("User")
                .build();

        Method normalizeMethod = User.class.getDeclaredMethod("normalizeEmail");
        normalizeMethod.setAccessible(true);
        normalizeMethod.invoke(user);

        assertThat(user.getEmail()).isEqualTo("test.user@ktab.org");
    }
}
