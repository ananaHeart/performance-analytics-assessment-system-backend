package com.capstone.assessment.v2.auth.repository;

import com.capstone.assessment.v2.auth.model.V2EmailVerificationOtp;
import com.capstone.assessment.v2.auth.model.V2EmailVerificationUser;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Profile("v2")
@Repository
public class V2EmailVerificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public V2EmailVerificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<V2EmailVerificationUser> findTeacherByEmail(String email) {
        return jdbcTemplate.query(
                """
                SELECT u.user_id, u.school_id, u.first_name, u.last_name, u.email,
                       s.status_name, u.email_verified_at
                  FROM users u
                  JOIN roles r ON r.role_id = u.role_id
                  JOIN statuses s ON s.status_id = u.status_id
                 WHERE u.email = ?
                   AND r.role_name = 'teacher'
                """,
                (rs, rowNum) -> new V2EmailVerificationUser(
                        rs.getLong("user_id"),
                        rs.getString("school_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("status_name"),
                        rs.getTimestamp("email_verified_at") != null
                ),
                email
        ).stream().findFirst();
    }

    public void invalidateUnusedCodes(long userId, Instant usedAt) {
        jdbcTemplate.update(
                "UPDATE email_verification_otps SET used_at = ? WHERE user_id = ? AND used_at IS NULL",
                Timestamp.from(usedAt),
                userId
        );
    }

    public void insertCode(
            long userId,
            String otpHash,
            int maxAttempts,
            Instant expiresAt,
            Instant resendAvailableAt,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO email_verification_otps (
                    user_id, otp_hash, max_attempts, expires_at, resend_available_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                userId,
                otpHash,
                maxAttempts,
                Timestamp.from(expiresAt),
                Timestamp.from(resendAvailableAt),
                Timestamp.from(createdAt)
        );
    }

    public Optional<V2EmailVerificationOtp> findLatestCodeForUpdate(long userId) {
        return jdbcTemplate.query(
                """
                SELECT email_verification_otp_id, user_id, otp_hash, attempt_count,
                       max_attempts, expires_at, resend_available_at, used_at
                  FROM email_verification_otps
                 WHERE user_id = ?
                 ORDER BY email_verification_otp_id DESC
                 LIMIT 1
                 FOR UPDATE
                """,
                (rs, rowNum) -> new V2EmailVerificationOtp(
                        rs.getLong("email_verification_otp_id"),
                        rs.getLong("user_id"),
                        rs.getString("otp_hash"),
                        rs.getInt("attempt_count"),
                        rs.getInt("max_attempts"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("resend_available_at").toInstant(),
                        rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()
                ),
                userId
        ).stream().findFirst();
    }

    public void incrementAttempts(long otpId) {
        jdbcTemplate.update(
                "UPDATE email_verification_otps SET attempt_count = attempt_count + 1 WHERE email_verification_otp_id = ?",
                otpId
        );
    }

    public void markUsed(long otpId, Instant usedAt) {
        jdbcTemplate.update(
                "UPDATE email_verification_otps SET used_at = ? WHERE email_verification_otp_id = ? AND used_at IS NULL",
                Timestamp.from(usedAt),
                otpId
        );
    }

    public int markEmailVerified(long userId, Instant verifiedAt) {
        return jdbcTemplate.update(
                """
                UPDATE users u
                JOIN statuses s ON s.status_id = u.status_id
                   SET u.email_verified_at = ?, u.updated_at = ?
                 WHERE u.user_id = ?
                   AND s.status_name = 'pending'
                   AND u.email_verified_at IS NULL
                """,
                Timestamp.from(verifiedAt),
                Timestamp.from(verifiedAt),
                userId
        );
    }
}
