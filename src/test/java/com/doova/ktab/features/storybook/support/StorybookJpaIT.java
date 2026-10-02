package com.doova.ktab.features.storybook.support;

import com.doova.ktab.config.jpa.JpaAuditingConfig;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real Postgres + Ktab's full Flyway history.
 *
 * <p>Uses a Testcontainers Postgres when Docker is available. Without Docker, set
 * {@code STORYBOOK_IT_DB_URL} (plus optional {@code STORYBOOK_IT_DB_USER} / {@code STORYBOOK_IT_DB_PASSWORD})
 * to a throwaway local database whose name ends in {@code _it}; the suffix guard keeps these tests
 * away from a real dev database. With neither available the tests are skipped.
 *
 * <p>The skip is wired through {@link ExtendWith} rather than {@code @EnabledIf}: {@code @EnabledIf} is
 * not {@code @Inherited}, so on an abstract base class it silently stops applying to every subclass
 * (JUnit 5 Javadoc). {@code @ExtendWith} <em>is</em> {@code @Inherited}, so {@link DatabaseAvailableCondition}
 * below still runs for all of this class's subclasses.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(StorybookJpaIT.DatabaseAvailableCondition.class)
@Import(JpaAuditingConfig.class)
public abstract class StorybookJpaIT {

    private static final String LOCAL_URL = System.getenv("STORYBOOK_IT_DB_URL");

    @Autowired
    protected TestEntityManager em;

    static boolean databaseAvailable() {
        return LOCAL_URL != null || DockerClientFactory.instance().isDockerAvailable();
    }

    static final class DatabaseAvailableCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            return databaseAvailable()
                    ? ConditionEvaluationResult.enabled("storybook IT database available")
                    : ConditionEvaluationResult.disabled(
                            "no STORYBOOK_IT_DB_URL and no Docker; skipping storybook JPA integration tests");
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (LOCAL_URL != null) {
            String database = LOCAL_URL.replaceFirst("\\?.*$", "").replaceFirst("^.*/", "");
            if (!database.endsWith("_it")) {
                throw new IllegalStateException(
                        "STORYBOOK_IT_DB_URL must point at a throwaway database whose name ends in _it, got: " + database);
            }
            registry.add("spring.datasource.url", () -> LOCAL_URL);
            registry.add("spring.datasource.username", () -> env("STORYBOOK_IT_DB_USER", "postgres"));
            registry.add("spring.datasource.password", () -> env("STORYBOOK_IT_DB_PASSWORD", ""));
        } else {
            PostgreSQLContainer<?> postgres = Container.INSTANCE;
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    /** Lazily started, shared across all ITs; Ryuk stops it when the JVM exits. */
    private static final class Container {
        static final PostgreSQLContainer<?> INSTANCE = start();

        private static PostgreSQLContainer<?> start() {
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
            return postgres;
        }
    }
}
