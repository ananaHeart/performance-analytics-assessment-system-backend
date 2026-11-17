package com.capstone.assessment.v3.account.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the Forgot Password lookups as real SQL (a mocked repository hid a broken query once). */
class V3AccountSettingsRepositoryTest {

    private JdbcTemplate jdbc;
    private V3AccountSettingsRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:reset-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
                CREATE TABLE statuses (status_id INT PRIMARY KEY, status_name VARCHAR(30));
                CREATE TABLE users (user_id BIGINT PRIMARY KEY, status_id INT, email VARCHAR(120),
                    email_verified_at TIMESTAMP, contact_number VARCHAR(20), contact_verified_at TIMESTAMP);
                INSERT INTO statuses VALUES (1, 'active'), (2, 'archived');
                INSERT INTO users VALUES (42, 1, 'ana@example.com', CURRENT_TIMESTAMP, '+639171234567', CURRENT_TIMESTAMP),
                    (43, 2, 'lloyd@example.com', CURRENT_TIMESTAMP, NULL, NULL);
                """);
        repository = new V3AccountSettingsRepository(jdbc);
    }

    @Test
    void theResetLookupIsValidSqlForEmailAndSms() {
        assertFalse(V3AccountSettingsRepository.resettableUserSql("email").contains("ANDLOWER"));
        assertEquals(Optional.of(42L), repository.findResettableUserId("email", "Ana@Example.com"));
        assertEquals(Optional.of(42L), repository.findResettableUserId("sms", "+639171234567"));
        assertTrue(repository.findResettableUserId("email", "nobody@example.com").isEmpty());
        // An archived account is not resettable; it gets the notice instead.
        assertTrue(repository.findResettableUserId("email", "lloyd@example.com").isEmpty());
        assertEquals(Optional.of("archived"), repository.findBlockedResetStatus("lloyd@example.com"));
        assertTrue(repository.findBlockedResetStatus("nobody@example.com").isEmpty());
    }
}
