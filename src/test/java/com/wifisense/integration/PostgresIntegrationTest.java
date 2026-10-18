package com.wifisense.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests against a real PostgreSQL built with the WiFiSense-database migrations, the whole backend
 * running (security filters, transactions, native SQL). They run when WIFISENSE_IT_DATABASE_URL is set, which
 * GitHub Actions does (see .github/workflows/backend.yml); without it they are skipped. JUnit does not inherit that
 * condition, so each test class carries {@code @EnabledIfEnvironmentVariable(named = DATABASE_URL, matches = ".+")}.
 * <p>
 * In GitHub Actions they connect as wifisense_app, the least-privilege role of the database: every query of the
 * backend is proven to work without owner permissions.
 * <p>
 * Every test creates its own data with unique names, so the database does not need to be empty.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "wifisense.security.jwt-secret=integration-tests-only-secret-0123456789abcdef",
        "wifisense.monitoring.enabled=false",
        "wifisense.accounts.admin-username=Jefe.Prueba",
        "wifisense.accounts.admin-password=Prueba#2026a",
        // a token presented twice is always a copy here: no retries to forgive
        "wifisense.session.reuse-grace=0s",
        "wifisense.retention.batch-size=100"})
abstract class PostgresIntegrationTest {

    static final String DATABASE_URL = "WIFISENSE_IT_DATABASE_URL";
    static final String ADMIN = "Jefe.Prueba";
    static final String PASSWORD = "Prueba#2026a";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv(DATABASE_URL));
        registry.add("spring.datasource.username", () -> System.getenv("WIFISENSE_IT_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("WIFISENSE_IT_DATABASE_PASSWORD"));
    }

    /** A short suffix that makes names unique between runs. */
    static String unique() {
        return Long.toString(System.nanoTime() % 2_000_000_000L, 36);
    }
}
