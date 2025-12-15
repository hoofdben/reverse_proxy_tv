package nl.mallepetrus.rptv.testutil;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class PostgresTestContainer {
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rptv")
            .withUsername("rptv")
            .withPassword("password");

    @BeforeAll
    static void startContainer() {
        try {
            postgres.start();
        } catch (Throwable t) {
            // Skip tests gracefully when Docker is unavailable
            Assumptions.assumeTrue(false, "[TEST_SKIP] Docker not available for Testcontainers: " + t.getMessage());
        }
    }

    @AfterAll
    static void stopContainer() {
        postgres.stop();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        // Disable prod profile
        registry.add("spring.profiles.active", () -> "test");
    }
}
