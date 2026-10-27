package com.capstone.assessment.v3.account.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** SET-1, SET-3, SET-6, VER-1: the signed-in user's own account and its verification codes. */
@Profile("v3")
@Repository
public class V3AccountSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public V3AccountSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AccountRow> lockAccount(long userId) {
        return jdbcTemplate.query("""
                SELECT u.user_id, role.role_name, u.first_name, u.middle_name, u.last_name, u.suffix_id,
                       u.email, u.email_verified_at, u.contact_number, u.contact_verified_at,
                       u.password_hash, u.password_changed_at
                  FROM users u
                  JOIN roles role ON role.role_id = u.role_id
                 WHERE u.user_id = ?
                   FOR UPDATE
                """, this::mapAccount, userId).stream().findFirst();
    }

    /** SET-1: when the email or contact number was last changed (from the audit log). */
    public Optional<Instant> findLastChange(long userId, String auditAction) {
        Timestamp last = jdbcTemplate.queryForObject("""
                SELECT MAX(created_at)
                  FROM audit_logs
                 WHERE user_id = ? AND action = ? AND outcome = 'success'
                """, Timestamp.class, userId, auditAction);
        return Optional.ofNullable(last).map(Timestamp::toInstant);
    }

    /** REG-3: the main major, which is never repeated as an additional major. */
    public Integer findMajorId(long userId) {
        return jdbcTemplate.queryForObject("SELECT major_id FROM users WHERE user_id = ?", Integer.class, userId);
    }

    public boolean emailUsedByAnother(String email, long userId) {
        return exists("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?) AND user_id <> ?", email, userId);
    }

    public boolean contactUsedByAnother(String contactNumber, long userId) {
        return exists("SELECT COUNT(*) FROM users WHERE contact_number = ? AND user_id <> ?", contactNumber, userId);
    }

    /** Codes sent for account settings (not registration) since the given time, for the daily limit. */
    public int countAccountCodesSince(long userId, Instant since) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM verification_challenges
                 WHERE user_id = ?
                   AND verification_purpose IN ('email_verification', 'contact_verification', 'sensitive_action')
                   AND created_at >= ?
                """, Integer.class, userId, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    public long insertChallenge(
            String challengeUuid,
            long userId,
            String purpose,
            String channel,
            String destinationMasked,
            String codeHash,
            int maximumAttempts,
            int resendCount,
            Instant nextResendAt,
            Instant expiresAt,
            Instant createdAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO verification_challenges (
                    challenge_uuid, user_id, verification_purpose, delivery_channel, destination_masked,
                    code_hash, challenge_status, delivery_status, attempt_count, maximum_attempt_count,
                    resend_count, next_resend_at, expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'pending', 'queued', 0, ?, ?, ?, ?, ?, ?)
                """, challengeUuid, userId, purpose, channel, destinationMasked, codeHash, maximumAttempts,
                resendCount, Timestamp.from(nextResendAt), Timestamp.from(expiresAt), Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        Long id = jdbcTemplate.queryForObject(
                "SELECT verification_challenge_id FROM verification_challenges WHERE challenge_uuid = ?",
                Long.class, challengeUuid);
        return id == null ? 0 : id;
    }

    public Optional<ChallengeRow> lockChallenge(String challengeUuid, long userId) {
        return jdbcTemplate.query("""
                SELECT verification_challenge_id, challenge_uuid, verification_purpose, delivery_channel,
                       code_hash, challenge_status, delivery_status, attempt_count, maximum_attempt_count,
                       resend_count, next_resend_at, expires_at
                  FROM verification_challenges
                 WHERE challenge_uuid = ? AND user_id = ?
                   FOR UPDATE
                """, (rs, rowNum) -> new ChallengeRow(
                rs.getLong("verification_challenge_id"),
                rs.getString("challenge_uuid"),
                rs.getString("verification_purpose"),
                rs.getString("delivery_channel"),
                rs.getString("code_hash"),
                rs.getString("challenge_status"),
                rs.getString("delivery_status"),
                rs.getInt("attempt_count"),
                rs.getInt("maximum_attempt_count"),
                rs.getInt("resend_count"),
                instant(rs, "next_resend_at"),
                instant(rs, "expires_at")
        ), challengeUuid, userId).stream().findFirst();
    }

    public int updateEmail(long userId, String email, Instant verifiedAt) {
        return jdbcTemplate.update(
                "UPDATE users SET email = ?, email_verified_at = ?, updated_at = ? WHERE user_id = ?",
                email, Timestamp.from(verifiedAt), Timestamp.from(verifiedAt), userId);
    }

    public int updateContact(long userId, String contactNumber, Instant verifiedAt) {
        return jdbcTemplate.update(
                "UPDATE users SET contact_number = ?, contact_verified_at = ?, updated_at = ? WHERE user_id = ?",
                contactNumber, Timestamp.from(verifiedAt), Timestamp.from(verifiedAt), userId);
    }

    /** VER-1: only the timestamp; the account status was settled at registration. */
    public int markVerified(long userId, String channel, Instant verifiedAt) {
        String column = "sms".equals(channel) ? "contact_verified_at" : "email_verified_at";
        return jdbcTemplate.update(
                "UPDATE users SET " + column + " = ?, updated_at = ? WHERE user_id = ? AND " + column + " IS NULL",
                Timestamp.from(verifiedAt), Timestamp.from(verifiedAt), userId);
    }

    public int updatePassword(long userId, String passwordHash, Instant changedAt) {
        return jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, password_changed_at = ?, updated_at = ? WHERE user_id = ?",
                passwordHash, Timestamp.from(changedAt), Timestamp.from(changedAt), userId);
    }

    public int updateName(long userId, String firstName, String middleName, String lastName, Integer suffixId,
                          Instant updatedAt) {
        return jdbcTemplate.update("""
                UPDATE users
                   SET first_name = ?, middle_name = ?, last_name = ?, suffix_id = ?, updated_at = ?
                 WHERE user_id = ?
                """, firstName, middleName, lastName, suffixId, Timestamp.from(updatedAt), userId);
    }

    private boolean exists(String sql, Object... arguments) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return count != null && count > 0;
    }

    private AccountRow mapAccount(ResultSet rs, int rowNum) throws SQLException {
        int suffixId = rs.getInt("suffix_id");
        Integer suffix = rs.wasNull() ? null : suffixId;
        return new AccountRow(
                rs.getLong("user_id"),
                rs.getString("role_name"),
                rs.getString("first_name"),
                rs.getString("middle_name"),
                rs.getString("last_name"),
                suffix,
                rs.getString("email"),
                instant(rs, "email_verified_at"),
                rs.getString("contact_number"),
                instant(rs, "contact_verified_at"),
                rs.getString("password_hash"),
                instant(rs, "password_changed_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record AccountRow(
            long userId,
            String role,
            String firstName,
            String middleName,
            String lastName,
            Integer suffixId,
            String email,
            Instant emailVerifiedAt,
            String contactNumber,
            Instant contactVerifiedAt,
            String passwordHash,
            Instant passwordChangedAt
    ) {
    }

    public record ChallengeRow(
            long challengeId,
            String challengeUuid,
            String purpose,
            String channel,
            String codeHash,
            String status,
            String deliveryStatus,
            int attemptCount,
            int maximumAttemptCount,
            int resendCount,
            Instant nextResendAt,
            Instant expiresAt
    ) {
    }
}
