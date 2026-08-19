package com.capstone.assessment.v2.auth.repository;

import com.capstone.assessment.v2.auth.model.V2AuthenticatedUser;
import com.capstone.assessment.v2.auth.model.V2LoginUser;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Profile("v2")
@Repository
public class V2AuthRepository {

    private static final String USER_SELECT = """
            SELECT u.user_id,
                   u.school_id,
                   u.first_name,
                   u.middle_name,
                   u.last_name,
                   u.suffix,
                   u.email,
                   u.password_hash,
                   r.role_name,
                   s.status_name,
                   u.failed_login_count,
                   u.locked_until_at,
                   u.email_verified_at
            FROM users u
            JOIN roles r ON r.role_id = u.role_id
            JOIN statuses s ON s.status_id = u.status_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public V2AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<V2LoginUser> findLoginUserByEmail(String email) {
        return jdbcTemplate.query(
                        USER_SELECT + " WHERE LOWER(u.email) = LOWER(?)",
                        this::mapLoginUser,
                        email
                ).stream()
                .findFirst();
    }

    public Optional<V2LoginUser> findLoginUserById(Long userId) {
        return jdbcTemplate.query(
                        USER_SELECT + " WHERE u.user_id = ?",
                        this::mapLoginUser,
                        userId
                ).stream()
                .findFirst();
    }

    public int countRecentFailedAttempts(String email, String ipAddress, Instant since) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM login_attempts
                WHERE attempted_at >= ?
                  AND was_successful = FALSE
                  AND (LOWER(attempted_email) = LOWER(?) OR (? IS NOT NULL AND ip_address = ?))
                """,
                Long.class,
                Timestamp.from(since),
                email,
                ipAddress,
                ipAddress
        );
        return count == null ? 0 : Math.toIntExact(count);
    }

    public void recordLoginAttempt(
            Long userId,
            String email,
            String ipAddress,
            String deviceIdentifier,
            String userAgent,
            boolean successful,
            String failureReason,
            Instant attemptedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO login_attempts (
                    user_id,
                    attempted_email,
                    ip_address,
                    device_identifier,
                    user_agent,
                    was_successful,
                    failure_reason,
                    attempted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                email,
                ipAddress,
                deviceIdentifier,
                userAgent,
                successful,
                failureReason,
                Timestamp.from(attemptedAt)
        );
    }

    public void recordFailedLogin(Long userId, int failedLoginCount, Instant lockedUntilAt) {
        jdbcTemplate.update(
                """
                UPDATE users
                SET failed_login_count = ?,
                    locked_until_at = ?
                WHERE user_id = ?
                """,
                failedLoginCount,
                lockedUntilAt == null ? null : Timestamp.from(lockedUntilAt),
                userId
        );
    }

    public void recordSuccessfulLogin(Long userId, Instant loginAt) {
        jdbcTemplate.update(
                """
                UPDATE users
                SET failed_login_count = 0,
                    locked_until_at = NULL,
                    last_login_at = ?
                WHERE user_id = ?
                """,
                Timestamp.from(loginAt),
                userId
        );
    }

    public void createSession(
            String sessionUuid,
            Long userId,
            String tokenHash,
            String deviceIdentifier,
            String ipAddress,
            String userAgent,
            Instant issuedAt,
            Instant expiresAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO auth_sessions (
                    session_uuid,
                    user_id,
                    refresh_token_hash,
                    device_identifier,
                    ip_address,
                    user_agent,
                    issued_at,
                    expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sessionUuid,
                userId,
                tokenHash,
                deviceIdentifier,
                ipAddress,
                userAgent,
                Timestamp.from(issuedAt),
                Timestamp.from(expiresAt)
        );
    }

    public Optional<V2AuthenticatedUser> findAuthenticatedUserByTokenHash(String tokenHash, Instant now) {
        return jdbcTemplate.query(
                        """
                        SELECT u.user_id,
                               u.school_id,
                               u.email,
                               r.role_name,
                               s.status_name,
                               auth.session_uuid
                        FROM auth_sessions auth
                        JOIN users u ON u.user_id = auth.user_id
                        JOIN roles r ON r.role_id = u.role_id
                        JOIN statuses s ON s.status_id = u.status_id
                        WHERE auth.refresh_token_hash = ?
                          AND auth.revoked_at IS NULL
                          AND auth.expires_at > ?
                          AND s.status_name = 'active'
                        """,
                        (resultSet, rowNumber) -> new V2AuthenticatedUser(
                                resultSet.getLong("user_id"),
                                resultSet.getString("school_id"),
                                resultSet.getString("email"),
                                resultSet.getString("role_name"),
                                resultSet.getString("status_name"),
                                resultSet.getString("session_uuid")
                        ),
                        tokenHash,
                        Timestamp.from(now)
                ).stream()
                .findFirst();
    }

    public void touchSession(String sessionUuid, Instant usedAt) {
        jdbcTemplate.update(
                """
                UPDATE auth_sessions
                SET last_used_at = ?
                WHERE session_uuid = ?
                  AND revoked_at IS NULL
                """,
                Timestamp.from(usedAt),
                sessionUuid
        );
    }

    public int revokeSession(String sessionUuid, Long userId, Instant revokedAt) {
        return jdbcTemplate.update(
                """
                UPDATE auth_sessions
                SET revoked_at = ?
                WHERE session_uuid = ?
                  AND user_id = ?
                  AND revoked_at IS NULL
                """,
                Timestamp.from(revokedAt),
                sessionUuid,
                userId
        );
    }

    public void recordAudit(
            String auditUuid,
            Long userId,
            String action,
            String entityType,
            String entityId,
            String outcome,
            String ipAddress,
            String deviceIdentifier,
            String userAgent,
            String details,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    audit_uuid,
                    user_id,
                    action,
                    entity_type,
                    entity_id,
                    outcome,
                    ip_address,
                    device_identifier,
                    user_agent,
                    details,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                auditUuid,
                userId,
                action,
                entityType,
                entityId,
                outcome,
                ipAddress,
                deviceIdentifier,
                userAgent,
                details,
                Timestamp.from(createdAt)
        );
    }

    private V2LoginUser mapLoginUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new V2LoginUser(
                resultSet.getLong("user_id"),
                resultSet.getString("school_id"),
                resultSet.getString("first_name"),
                resultSet.getString("middle_name"),
                resultSet.getString("last_name"),
                resultSet.getString("suffix"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getString("role_name"),
                resultSet.getString("status_name"),
                resultSet.getInt("failed_login_count"),
                toInstant(resultSet.getTimestamp("locked_until_at")),
                toInstant(resultSet.getTimestamp("email_verified_at"))
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
