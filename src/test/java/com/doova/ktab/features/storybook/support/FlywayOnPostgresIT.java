package com.doova.ktab.features.storybook.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayOnPostgresIT extends StorybookJpaIT {

    @Test
    void theWholeKtabMigrationHistoryApplies() {
        Number users = (Number) em.getEntityManager()
                .createNativeQuery("select count(*) from information_schema.tables where table_name = 'tbl_users'")
                .getSingleResult();
        assertThat(users.intValue()).isEqualTo(1);
    }

    @Test
    void canCreateAReader() {
        assertThat(UserFixtures.reader(em, "parent@example.com").getId()).isNotNull();
    }
}
