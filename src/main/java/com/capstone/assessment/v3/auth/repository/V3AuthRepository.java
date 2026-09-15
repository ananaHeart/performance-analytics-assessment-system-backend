package com.capstone.assessment.v3.auth.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.auth.model.V3UserAccount;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3AuthRepository {

    private static final String USER_SELECT = """
            SELECT u.user_id,
                   u.school_id,
                   u.address_id,
                   u.first_name,
                   u.middle_name,
                   u.last_name,
                   suffix.suffix_name AS suffix,
                   u.email,
                   u.password_hash,
                   role.role_name,
                   status.status_name,
                   u.failed_login_count,
                   u.locked_until_at,
                   u.email_verified_at,
                   u.mfa_required,
                   u.created_at
              FROM users u
              JOIN roles role ON role.role_id = u.role_id
              JOIN statuses status ON status.status_id = u.status_id
              LEFT JOIN suffixes suffix ON suffix.suffix_id = u.suffix_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public V3AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<V3UserAccount> findUserByEmail(String email) {
        return jdbcTemplate.query(
                USER_SELECT + " WHERE LOWER(u.email) = LOWER(?)",
                this::mapUser,
                email
        ).stream().findFirst();
    }

    public Optional<V3UserAccount> findUserById(long userId) {
        return jdbcTemplate.query(
                USER_SELECT + " WHERE u.user_id = ?",
                this::mapUser,
                userId
        ).stream().findFirst();
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

    public int countRecentCredentialFailures(String email, String ipAddress, Instant since) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM login_attempts
                 WHERE attempted_at >= ?
                   AND was_successful = FALSE
                   AND failure_reason = 'invalid_credentials'
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

    public int countRecentMfaFailures(long userId, Instant since) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM login_attempts
                 WHERE user_id = ?
                   AND attempted_at >= ?
                   AND was_successful = FALSE
                   AND failure_reason IN ('invalid_mfa_code', 'mfa_locked')
                """,
                Integer.class,
                userId,
                Timestamp.from(since)
        );
        return count == null ? 0 : count;
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
                    user_id, attempted_email, ip_address, device_identifier, user_agent,
                    was_successful, failure_reason, attempted_at
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

    public void recordFailedLogin(long userId, int failedLoginCount, Instant lockedUntilAt) {
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

    public void recordSuccessfulLogin(long userId, Instant loginAt) {
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
            long userId,
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
                    session_uuid, user_id, authentication_level, refresh_token_hash,
                    device_identifier, ip_address, user_agent, issued_at, expires_at
                ) VALUES (?, ?, 'password', ?, ?, ?, ?, ?, ?)
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

    public void createMfaSession(
            String sessionUuid,
            long userId,
            long factorId,
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
                    session_uuid, user_id, authentication_level, user_mfa_factor_id,
                    mfa_verified_at, refresh_token_hash, device_identifier, ip_address,
                    user_agent, issued_at, expires_at
                ) VALUES (?, ?, 'mfa', ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sessionUuid,
                userId,
                factorId,
                Timestamp.from(issuedAt),
                tokenHash,
                deviceIdentifier,
                ipAddress,
                userAgent,
                Timestamp.from(issuedAt),
                Timestamp.from(expiresAt)
        );
    }

    public int upgradeSessionToMfa(
            String sessionUuid,
            long userId,
            long factorId,
            Instant verifiedAt
    ) {
        return jdbcTemplate.update(
                """
                UPDATE auth_sessions
                   SET authentication_level = 'mfa',
                       user_mfa_factor_id = ?,
                       mfa_verified_at = ?,
                       last_used_at = ?
                 WHERE session_uuid = ?
                   AND user_id = ?
                   AND revoked_at IS NULL
                   AND expires_at > ?
                """,
                factorId,
                Timestamp.from(verifiedAt),
                Timestamp.from(verifiedAt),
                sessionUuid,
                userId,
                Timestamp.from(verifiedAt)
        );
    }

    public Optional<V3AuthenticatedUser> findAuthenticatedUserByTokenHash(String tokenHash, Instant now) {
        return jdbcTemplate.query(
                """
                SELECT u.user_id,
                       u.school_id,
                       u.email,
                       role.role_name,
                       status.status_name,
                       session.session_uuid
                  FROM auth_sessions session
                  JOIN users u ON u.user_id = session.user_id
                  JOIN roles role ON role.role_id = u.role_id
                  JOIN statuses status ON status.status_id = u.status_id
                 WHERE session.refresh_token_hash = ?
                   AND session.revoked_at IS NULL
                   AND session.expires_at > ?
                   AND status.status_name = 'active'
                   AND (u.mfa_required = FALSE OR session.authentication_level = 'mfa')
                """,
                (resultSet, rowNumber) -> new V3AuthenticatedUser(
                        resultSet.getLong("user_id"),
                        resultSet.getString("school_id"),
                        resultSet.getString("email"),
                        resultSet.getString("role_name"),
                        resultSet.getString("status_name"),
                        resultSet.getString("session_uuid")
                ),
                tokenHash,
                Timestamp.from(now)
        ).stream().findFirst();
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

    public int revokeSession(String sessionUuid, long userId, Instant revokedAt) {
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

    public int revokeOtherSessions(long userId, String retainedSessionUuid, Instant revokedAt) {
        return jdbcTemplate.update(
                """
                UPDATE auth_sessions
                   SET revoked_at = ?
                 WHERE user_id = ?
                   AND session_uuid <> ?
                   AND revoked_at IS NULL
                """,
                Timestamp.from(revokedAt),
                userId,
                retainedSessionUuid
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
                    audit_uuid, user_id, action, entity_type, entity_id, outcome,
                    ip_address, device_identifier, user_agent, details, created_at
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

    private V3UserAccount mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new V3UserAccount(
                resultSet.getLong("user_id"),
                resultSet.getString("school_id"),
                resultSet.getLong("address_id"),
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
                toInstant(resultSet.getTimestamp("email_verified_at")),
                resultSet.getBoolean("mfa_required"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
