package com.capstone.assessment.v3.auth.repository;

import com.capstone.assessment.v3.auth.model.V3MfaAuthenticationChallenge;
import com.capstone.assessment.v3.auth.model.V3MfaFactor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3MfaRepository {

    private static final String FACTOR_SELECT = """
            SELECT user_mfa_factor_id,
                   factor_uuid,
                   user_id,
                   factor_name,
                   secret_ciphertext,
                   secret_key_version,
                   totp_algorithm,
                   totp_digits,
                   totp_period_seconds,
                   factor_status,
                   enrolled_at,
                   verified_at,
                   last_used_at
              FROM user_mfa_factors
            """;

    private static final RowMapper<V3MfaFactor> FACTOR_MAPPER = (resultSet, rowNumber) ->
            new V3MfaFactor(
                    resultSet.getLong("user_mfa_factor_id"),
                    resultSet.getString("factor_uuid"),
                    resultSet.getLong("user_id"),
                    resultSet.getString("factor_name"),
                    resultSet.getBytes("secret_ciphertext"),
                    resultSet.getInt("secret_key_version"),
                    resultSet.getString("totp_algorithm"),
                    resultSet.getInt("totp_digits"),
                    resultSet.getInt("totp_period_seconds"),
                    resultSet.getString("factor_status"),
                    resultSet.getTimestamp("enrolled_at").toInstant(),
                    toInstant(resultSet.getTimestamp("verified_at")),
                    toInstant(resultSet.getTimestamp("last_used_at"))
            );

    private static final RowMapper<V3MfaAuthenticationChallenge> CHALLENGE_MAPPER = (resultSet, rowNumber) ->
            new V3MfaAuthenticationChallenge(
                    resultSet.getLong("mfa_authentication_challenge_id"),
                    resultSet.getString("challenge_uuid"),
                    resultSet.getLong("user_id"),
                    resultSet.getLong("user_mfa_factor_id"),
                    resultSet.getString("challenge_status"),
                    resultSet.getInt("attempt_count"),
                    resultSet.getInt("maximum_attempt_count"),
                    resultSet.getTimestamp("expires_at").toInstant(),
                    toInstant(resultSet.getTimestamp("verified_at")),
                    resultSet.getTimestamp("created_at").toInstant()
            );

    private final JdbcTemplate jdbcTemplate;

    public V3MfaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<V3MfaFactor> findActiveFactorByUserId(long userId) {
        return jdbcTemplate.query(
                FACTOR_SELECT + " WHERE user_id = ? AND factor_status = 'active'",
                FACTOR_MAPPER,
                userId
        ).stream().findFirst();
    }

    public Optional<V3MfaFactor> findFactorByUuidAndUserId(String factorUuid, long userId) {
        return jdbcTemplate.query(
                FACTOR_SELECT + " WHERE factor_uuid = ? AND user_id = ?",
                FACTOR_MAPPER,
                factorUuid,
                userId
        ).stream().findFirst();
    }

    public Optional<V3MfaFactor> findFactorById(long factorId) {
        return jdbcTemplate.query(
                FACTOR_SELECT + " WHERE user_mfa_factor_id = ?",
                FACTOR_MAPPER,
                factorId
        ).stream().findFirst();
    }

    public void revokePendingFactors(long userId, Instant revokedAt) {
        jdbcTemplate.update(
                """
                UPDATE user_mfa_factors
                   SET factor_status = 'revoked',
                       revoked_at = ?
                 WHERE user_id = ?
                   AND factor_status = 'pending'
                """,
                Timestamp.from(revokedAt),
                userId
        );
    }

    public long createPendingFactor(
            String factorUuid,
            long userId,
            String factorName,
            byte[] secretCiphertext,
            int secretKeyVersion,
            Instant enrolledAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO user_mfa_factors (
                        factor_uuid, user_id, factor_type, factor_name, secret_ciphertext,
                        secret_key_version, totp_algorithm, totp_digits, totp_period_seconds,
                        factor_status, enrolled_at, created_at, updated_at
                    ) VALUES (?, ?, 'totp', ?, ?, ?, 'SHA1', 6, 30, 'pending', ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, factorUuid);
            statement.setLong(2, userId);
            statement.setString(3, factorName);
            statement.setBytes(4, secretCiphertext);
            statement.setInt(5, secretKeyVersion);
            statement.setTimestamp(6, Timestamp.from(enrolledAt));
            statement.setTimestamp(7, Timestamp.from(enrolledAt));
            statement.setTimestamp(8, Timestamp.from(enrolledAt));
            return statement;
        }, keyHolder);
        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("The MFA factor identifier was not generated.");
        }
        return keyHolder.getKey().longValue();
    }

    public int activateFactor(long factorId, long userId, Instant verifiedAt) {
        return jdbcTemplate.update(
                """
                UPDATE user_mfa_factors
                   SET factor_status = 'active',
                       verified_at = ?,
                       disabled_at = NULL,
                       revoked_at = NULL
                 WHERE user_mfa_factor_id = ?
                   AND user_id = ?
                   AND factor_status = 'pending'
                """,
                Timestamp.from(verifiedAt),
                factorId,
                userId
        );
    }

    public void setUserMfaRequired(long userId, boolean required) {
        jdbcTemplate.update(
                "UPDATE users SET mfa_required = ? WHERE user_id = ?",
                required,
                userId
        );
    }

    public int disableFactor(long factorId, long userId, Instant disabledAt) {
        return jdbcTemplate.update(
                """
                UPDATE user_mfa_factors
                   SET factor_status = 'disabled',
                       disabled_at = ?
                 WHERE user_mfa_factor_id = ?
                   AND user_id = ?
                   AND factor_status = 'active'
                """,
                Timestamp.from(disabledAt),
                factorId,
                userId
        );
    }

    public int countUnusedRecoveryCodes(long factorId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM mfa_recovery_codes
                 WHERE user_mfa_factor_id = ?
                   AND used_at IS NULL
                """,
                Integer.class,
                factorId
        );
        return count == null ? 0 : count;
    }

    public void replaceRecoveryCodes(
            long factorId,
            String batchUuid,
            List<String> codeHashes,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                "DELETE FROM mfa_recovery_codes WHERE user_mfa_factor_id = ?",
                factorId
        );
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO mfa_recovery_codes (
                    user_mfa_factor_id, recovery_batch_uuid, code_hash, created_at
                ) VALUES (?, ?, ?, ?)
                """,
                codeHashes,
                codeHashes.size(),
                (statement, codeHash) -> {
                    statement.setLong(1, factorId);
                    statement.setString(2, batchUuid);
                    statement.setString(3, codeHash);
                    statement.setTimestamp(4, Timestamp.from(createdAt));
                }
        );
    }

    public int consumeRecoveryCode(long factorId, String codeHash, Instant usedAt) {
        return jdbcTemplate.update(
                """
                UPDATE mfa_recovery_codes
                   SET used_at = ?
                 WHERE user_mfa_factor_id = ?
                   AND code_hash = ?
                   AND used_at IS NULL
                """,
                Timestamp.from(usedAt),
                factorId,
                codeHash
        );
    }

    public void deleteRecoveryCodes(long factorId) {
        jdbcTemplate.update(
                "DELETE FROM mfa_recovery_codes WHERE user_mfa_factor_id = ?",
                factorId
        );
    }

    public void cancelPendingChallenges(long userId, Instant updatedAt) {
        jdbcTemplate.update(
                """
                UPDATE mfa_authentication_challenges
                   SET challenge_status = 'cancelled',
                       updated_at = ?
                 WHERE user_id = ?
                   AND challenge_status = 'pending'
                """,
                Timestamp.from(updatedAt),
                userId
        );
    }

    public void createAuthenticationChallenge(
            String challengeUuid,
            long userId,
            long factorId,
            int maximumAttempts,
            Instant expiresAt,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO mfa_authentication_challenges (
                    challenge_uuid, user_id, user_mfa_factor_id, challenge_status,
                    attempt_count, maximum_attempt_count, expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, 'pending', 0, ?, ?, ?, ?)
                """,
                challengeUuid,
                userId,
                factorId,
                maximumAttempts,
                Timestamp.from(expiresAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    public Optional<V3MfaAuthenticationChallenge> findChallengeByUuidForUpdate(String challengeUuid) {
        return jdbcTemplate.query(
                """
                SELECT mfa_authentication_challenge_id,
                       challenge_uuid,
                       user_id,
                       user_mfa_factor_id,
                       challenge_status,
                       attempt_count,
                       maximum_attempt_count,
                       expires_at,
                       verified_at,
                       created_at
                  FROM mfa_authentication_challenges
                 WHERE challenge_uuid = ?
                 FOR UPDATE
                """,
                CHALLENGE_MAPPER,
                challengeUuid
        ).stream().findFirst();
    }

    public int recordFailedChallengeAttempt(
            long challengeId,
            int expectedAttemptCount,
            int nextAttemptCount,
            boolean locked,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                UPDATE mfa_authentication_challenges
                   SET attempt_count = ?,
                       challenge_status = ?,
                       updated_at = ?
                 WHERE mfa_authentication_challenge_id = ?
                   AND challenge_status = 'pending'
                   AND attempt_count = ?
                """,
                nextAttemptCount,
                locked ? "locked" : "pending",
                Timestamp.from(updatedAt),
                challengeId,
                expectedAttemptCount
        );
    }

    public int expireChallenge(long challengeId, Instant updatedAt) {
        return jdbcTemplate.update(
                """
                UPDATE mfa_authentication_challenges
                   SET challenge_status = 'expired',
                       updated_at = ?
                 WHERE mfa_authentication_challenge_id = ?
                   AND challenge_status = 'pending'
                """,
                Timestamp.from(updatedAt),
                challengeId
        );
    }

    public int verifyChallenge(long challengeId, Instant verifiedAt) {
        return jdbcTemplate.update(
                """
                UPDATE mfa_authentication_challenges
                   SET challenge_status = 'verified',
                       verified_at = ?,
                       updated_at = ?
                 WHERE mfa_authentication_challenge_id = ?
                   AND challenge_status = 'pending'
                """,
                Timestamp.from(verifiedAt),
                Timestamp.from(verifiedAt),
                challengeId
        );
    }

    public int markFactorUsed(long factorId, Instant acceptedCounterStart) {
        return jdbcTemplate.update(
                """
                UPDATE user_mfa_factors
                   SET last_used_at = ?
                 WHERE user_mfa_factor_id = ?
                   AND factor_status = 'active'
                   AND (last_used_at IS NULL OR last_used_at < ?)
                """,
                Timestamp.from(acceptedCounterStart),
                factorId,
                Timestamp.from(acceptedCounterStart)
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
