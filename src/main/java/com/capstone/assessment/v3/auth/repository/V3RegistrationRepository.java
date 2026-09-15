package com.capstone.assessment.v3.auth.repository;

import com.capstone.assessment.v3.auth.dto.V3RegistrationReferenceDataResponse;
import com.capstone.assessment.v3.auth.dto.V3TeacherRegistrationRequest;
import com.capstone.assessment.v3.auth.model.V3VerificationChallenge;
import com.capstone.assessment.v3.auth.model.V3ProvisionalUser;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3RegistrationRepository {

    private static final String CHALLENGE_SELECT = """
            SELECT challenge.verification_challenge_id,
                   challenge.challenge_uuid,
                   challenge.user_id,
                   u.address_id,
                   u.school_id,
                   u.email,
                   challenge.destination_masked,
                   challenge.code_hash,
                   challenge.challenge_status,
                   challenge.delivery_status,
                   challenge.attempt_count,
                   challenge.maximum_attempt_count,
                   challenge.resend_count,
                   challenge.next_resend_at,
                   challenge.expires_at,
                   u.created_at AS user_created_at,
                   status.status_name AS account_status,
                   u.email_verified_at
              FROM verification_challenges challenge
              JOIN users u ON u.user_id = challenge.user_id
              JOIN statuses status ON status.status_id = u.status_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public V3RegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<V3RegistrationReferenceDataResponse.ReferenceOption> listGenders() {
        return jdbcTemplate.query(
                "SELECT gender_id, gender_name FROM genders ORDER BY gender_name",
                (resultSet, rowNumber) -> new V3RegistrationReferenceDataResponse.ReferenceOption(
                        resultSet.getLong("gender_id"), resultSet.getString("gender_name"))
        );
    }

    public List<V3RegistrationReferenceDataResponse.ReferenceOption> listActiveSuffixes() {
        return jdbcTemplate.query(
                """
                SELECT suffix_id, suffix_name
                  FROM suffixes
                 WHERE is_active = TRUE
                 ORDER BY display_order, suffix_name
                """,
                (resultSet, rowNumber) -> new V3RegistrationReferenceDataResponse.ReferenceOption(
                        resultSet.getLong("suffix_id"), resultSet.getString("suffix_name"))
        );
    }

    public List<V3RegistrationReferenceDataResponse.ReferenceOption> listMajors() {
        return jdbcTemplate.query(
                "SELECT major_id, major_name FROM majors ORDER BY major_name",
                (resultSet, rowNumber) -> new V3RegistrationReferenceDataResponse.ReferenceOption(
                        resultSet.getLong("major_id"), resultSet.getString("major_name"))
        );
    }

    public List<V3RegistrationReferenceDataResponse.ReferenceOption> listActiveEducationalAttainments() {
        return jdbcTemplate.query(
                """
                SELECT educational_attainment_id, attainment_name
                  FROM educational_attainments
                 WHERE is_active = TRUE
                 ORDER BY attainment_order, attainment_name
                """,
                (resultSet, rowNumber) -> new V3RegistrationReferenceDataResponse.ReferenceOption(
                        resultSet.getLong("educational_attainment_id"), resultSet.getString("attainment_name"))
        );
    }

    public List<V3RegistrationReferenceDataResponse.SchoolOption> listSchools() {
        return jdbcTemplate.query(
                "SELECT school_id, school_name FROM school_profiles ORDER BY school_name, school_id",
                (resultSet, rowNumber) -> new V3RegistrationReferenceDataResponse.SchoolOption(
                        resultSet.getString("school_id"), resultSet.getString("school_name"))
        );
    }

    public Optional<String> findSchoolId(String schoolCode) {
        return jdbcTemplate.query(
                "SELECT school_id FROM school_profiles WHERE LOWER(school_id) = LOWER(?)",
                (resultSet, rowNumber) -> resultSet.getString("school_id"),
                schoolCode
        ).stream().findFirst();
    }

    public boolean emailExists(String email) {
        return exists("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)", email);
    }

    public boolean contactExists(String contactNumber) {
        return exists("SELECT COUNT(*) FROM users WHERE contact_number = ?", contactNumber);
    }

    public boolean genderExists(Integer genderId) {
        return exists("SELECT COUNT(*) FROM genders WHERE gender_id = ?", genderId);
    }

    public boolean majorExists(Integer majorId) {
        return exists("SELECT COUNT(*) FROM majors WHERE major_id = ?", majorId);
    }

    public boolean suffixExists(Integer suffixId) {
        return exists("SELECT COUNT(*) FROM suffixes WHERE suffix_id = ? AND is_active = TRUE", suffixId);
    }

    public boolean educationalAttainmentExists(Integer attainmentId) {
        return exists(
                "SELECT COUNT(*) FROM educational_attainments WHERE educational_attainment_id = ? AND is_active = TRUE",
                attainmentId
        );
    }

    public int findRoleId(String roleName) {
        return requiredReferenceId("SELECT role_id FROM roles WHERE role_name = ?", roleName);
    }

    public int findStatusId(String statusName) {
        return requiredReferenceId(
                "SELECT status_id FROM statuses WHERE status_name = ? AND is_active = TRUE",
                statusName
        );
    }

    public long insertAddress(V3TeacherRegistrationRequest.Address address) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO addresses (
                        country_code, region_code, region_name, province_code, province_name,
                        city_municipality_code, city_municipality_name, barangay_code,
                        barangay_name, address_line, postal_code, address_source
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, valueOrDefault(address.countryCode(), "PH").toUpperCase());
            statement.setString(2, trimToNull(address.regionCode()));
            statement.setString(3, trimToNull(address.regionName()));
            statement.setString(4, trimToNull(address.provinceCode()));
            statement.setString(5, trimToNull(address.provinceName()));
            statement.setString(6, trimToNull(address.cityMunicipalityCode()));
            statement.setString(7, address.cityMunicipalityName().trim());
            statement.setString(8, trimToNull(address.barangayCode()));
            statement.setString(9, address.barangayName().trim());
            statement.setString(10, address.addressLine().trim());
            statement.setString(11, trimToNull(address.postalCode()));
            statement.setString(12, addressSource(address));
            return statement;
        }, keyHolder);
        return requiredGeneratedKey(keyHolder, "address");
    }

    public long insertTeacher(
            String schoolId,
            long addressId,
            int roleId,
            int statusId,
            V3TeacherRegistrationRequest request,
            String email,
            String contactNumber,
            String passwordHash,
            Instant createdAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO users (
                        school_id, address_id, gender_id, major_id, educational_attainment_id,
                        role_id, status_id, first_name, middle_name, last_name, suffix_id,
                        birth_date, teaching_start_month, teaching_start_year, email, contact_number,
                        password_hash, password_changed_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, schoolId);
            statement.setLong(2, addressId);
            statement.setInt(3, request.genderId());
            statement.setInt(4, request.majorId());
            statement.setInt(5, request.educationalAttainmentId());
            statement.setInt(6, roleId);
            statement.setInt(7, statusId);
            statement.setString(8, request.firstName().trim());
            statement.setString(9, trimToNull(request.middleName()));
            statement.setString(10, request.lastName().trim());
            if (request.suffixId() == null) {
                statement.setNull(11, java.sql.Types.TINYINT);
            } else {
                statement.setInt(11, request.suffixId());
            }
            statement.setDate(12, java.sql.Date.valueOf(request.birthDate()));
            statement.setInt(13, request.teachingStartMonth());
            statement.setInt(14, request.teachingStartYear());
            statement.setString(15, email);
            statement.setString(16, contactNumber);
            statement.setString(17, passwordHash);
            statement.setTimestamp(18, Timestamp.from(createdAt));
            statement.setTimestamp(19, Timestamp.from(createdAt));
            statement.setTimestamp(20, Timestamp.from(createdAt));
            return statement;
        }, keyHolder);
        return requiredGeneratedKey(keyHolder, "teacher registration");
    }

    public long insertChallenge(
            String challengeUuid,
            long userId,
            String destinationMasked,
            String codeHash,
            int maximumAttempts,
            int resendCount,
            Instant nextResendAt,
            Instant expiresAt,
            Instant createdAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO verification_challenges (
                        challenge_uuid, user_id, verification_purpose, delivery_channel,
                        destination_masked, code_hash, challenge_status, delivery_status,
                        attempt_count, maximum_attempt_count, resend_count, next_resend_at,
                        expires_at, created_at, updated_at
                    ) VALUES (?, ?, 'teacher_registration', 'email', ?, ?, 'pending', 'queued',
                              0, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, challengeUuid);
            statement.setLong(2, userId);
            statement.setString(3, destinationMasked);
            statement.setString(4, codeHash);
            statement.setInt(5, maximumAttempts);
            statement.setInt(6, resendCount);
            statement.setTimestamp(7, Timestamp.from(nextResendAt));
            statement.setTimestamp(8, Timestamp.from(expiresAt));
            statement.setTimestamp(9, Timestamp.from(createdAt));
            statement.setTimestamp(10, Timestamp.from(createdAt));
            return statement;
        }, keyHolder);
        return requiredGeneratedKey(keyHolder, "verification challenge");
    }

    public Optional<V3VerificationChallenge> findChallengeForUpdate(String challengeUuid) {
        return jdbcTemplate.query(
                CHALLENGE_SELECT + " WHERE challenge.challenge_uuid = ? FOR UPDATE",
                this::mapChallenge,
                challengeUuid
        ).stream().findFirst();
    }

    public Optional<V3VerificationChallenge> findLatestChallenge(long userId) {
        return jdbcTemplate.query(
                CHALLENGE_SELECT + """
                         WHERE challenge.user_id = ?
                           AND challenge.verification_purpose = 'teacher_registration'
                           AND challenge.delivery_channel = 'email'
                         ORDER BY challenge.created_at DESC, challenge.verification_challenge_id DESC
                         LIMIT 1
                        """,
                this::mapChallenge,
                userId
        ).stream().findFirst();
    }

    public int countVerificationMessagesSince(long userId, Instant since) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM verification_challenges
                 WHERE user_id = ?
                   AND verification_purpose = 'teacher_registration'
                   AND delivery_channel = 'email'
                   AND created_at >= ?
                """,
                Integer.class,
                userId,
                Timestamp.from(since)
        );
        return count == null ? 0 : count;
    }

    public void markDeliverySent(long challengeId, Instant sentAt, String provider) {
        jdbcTemplate.update(
                """
                UPDATE verification_challenges
                   SET delivery_status = 'sent',
                       delivery_provider = ?,
                       last_sent_at = ?,
                       updated_at = ?
                 WHERE verification_challenge_id = ?
                """,
                provider,
                Timestamp.from(sentAt),
                Timestamp.from(sentAt),
                challengeId
        );
    }

    public void markDeliveryFailed(long challengeId, Instant failedAt, String provider) {
        jdbcTemplate.update(
                """
                UPDATE verification_challenges
                   SET delivery_status = 'failed',
                       delivery_provider = ?,
                       updated_at = ?
                 WHERE verification_challenge_id = ?
                """,
                provider,
                Timestamp.from(failedAt),
                challengeId
        );
    }

    public void cancelChallenge(long challengeId, Instant cancelledAt) {
        jdbcTemplate.update(
                """
                UPDATE verification_challenges
                   SET challenge_status = 'cancelled',
                       updated_at = ?
                 WHERE verification_challenge_id = ?
                   AND challenge_status <> 'verified'
                """,
                Timestamp.from(cancelledAt),
                challengeId
        );
    }

    public void markChallengeExpired(long challengeId, Instant expiredAt) {
        jdbcTemplate.update(
                """
                UPDATE verification_challenges
                   SET challenge_status = 'expired',
                       updated_at = ?
                 WHERE verification_challenge_id = ?
                   AND challenge_status = 'pending'
                """,
                Timestamp.from(expiredAt),
                challengeId
        );
    }

    public void recordInvalidAttempt(long challengeId, int attemptCount, boolean locked, Instant attemptedAt) {
        jdbcTemplate.update(
                """
                UPDATE verification_challenges
                   SET attempt_count = ?,
                       challenge_status = ?,
                       updated_at = ?
                 WHERE verification_challenge_id = ?
                """,
                attemptCount,
                locked ? "locked" : "pending",
                Timestamp.from(attemptedAt),
                challengeId
        );
    }

    public void markChallengeVerified(long challengeId, Instant verifiedAt) {
        jdbcTemplate.update(
                """
                UPDATE verification_challenges
                   SET challenge_status = 'verified',
                       verified_at = ?,
                       updated_at = ?
                 WHERE verification_challenge_id = ?
                """,
                Timestamp.from(verifiedAt),
                Timestamp.from(verifiedAt),
                challengeId
        );
    }

    public void cancelOtherChallenges(long userId, long verifiedChallengeId, Instant cancelledAt) {
        jdbcTemplate.update(
                """
                UPDATE verification_challenges
                   SET challenge_status = 'cancelled',
                       updated_at = ?
                 WHERE user_id = ?
                   AND verification_purpose = 'teacher_registration'
                   AND verification_challenge_id <> ?
                   AND challenge_status = 'pending'
                """,
                Timestamp.from(cancelledAt),
                userId,
                verifiedChallengeId
        );
    }

    public int markUserEmailVerified(long userId, int pendingStatusId, int verifiedStatusId, Instant verifiedAt) {
        return jdbcTemplate.update(
                """
                UPDATE users
                   SET status_id = ?,
                       email_verified_at = ?,
                       updated_at = ?
                 WHERE user_id = ?
                   AND status_id = ?
                   AND email_verified_at IS NULL
                """,
                verifiedStatusId,
                Timestamp.from(verifiedAt),
                Timestamp.from(verifiedAt),
                userId,
                pendingStatusId
        );
    }

    public List<Long> findExpiredProvisionalUserIds(Instant cutoff, int limit) {
        return jdbcTemplate.query(
                """
                SELECT u.user_id
                  FROM users u
                  JOIN statuses status ON status.status_id = u.status_id
                 WHERE status.status_name = 'pending_email_verification'
                   AND u.email_verified_at IS NULL
                   AND u.created_at <= ?
                 ORDER BY u.created_at, u.user_id
                 LIMIT ?
                """,
                (resultSet, rowNumber) -> resultSet.getLong("user_id"),
                Timestamp.from(cutoff),
                limit
        );
    }

    public List<Long> findExpiredProvisionalUserIdsByIdentity(
            String email,
            String contactNumber,
            Instant cutoff
    ) {
        return jdbcTemplate.query(
                """
                SELECT u.user_id
                  FROM users u
                  JOIN statuses status ON status.status_id = u.status_id
                 WHERE status.status_name = 'pending_email_verification'
                   AND u.email_verified_at IS NULL
                   AND u.created_at <= ?
                   AND ((? IS NOT NULL AND LOWER(u.email) = LOWER(?))
                        OR (? IS NOT NULL AND u.contact_number = ?))
                 ORDER BY u.user_id
                """,
                (resultSet, rowNumber) -> resultSet.getLong("user_id"),
                Timestamp.from(cutoff),
                email,
                email,
                contactNumber,
                contactNumber
        );
    }

    public Optional<V3ProvisionalUser> findProvisionalUserForUpdate(long userId) {
        return jdbcTemplate.query(
                """
                SELECT u.user_id, u.address_id, u.email, u.created_at
                  FROM users u
                  JOIN statuses status ON status.status_id = u.status_id
                 WHERE u.user_id = ?
                   AND status.status_name = 'pending_email_verification'
                   AND u.email_verified_at IS NULL
                 FOR UPDATE
                """,
                (resultSet, rowNumber) -> new V3ProvisionalUser(
                        resultSet.getLong("user_id"),
                        resultSet.getLong("address_id"),
                        resultSet.getString("email"),
                        resultSet.getTimestamp("created_at").toInstant()
                ),
                userId
        ).stream().findFirst();
    }

    public void anonymizeLoginAttempts(long userId, String email, String replacement) {
        jdbcTemplate.update(
                """
                UPDATE login_attempts
                   SET attempted_email = ?
                 WHERE user_id = ?
                    OR LOWER(attempted_email) = LOWER(?)
                """,
                replacement,
                userId,
                email
        );
    }

    public int deleteExpiredProvisionalUser(long userId, Instant cutoff) {
        return jdbcTemplate.update(
                """
                DELETE u
                  FROM users u
                  JOIN statuses status ON status.status_id = u.status_id
                 WHERE u.user_id = ?
                   AND status.status_name = 'pending_email_verification'
                   AND u.email_verified_at IS NULL
                   AND u.created_at <= ?
                """,
                userId,
                Timestamp.from(cutoff)
        );
    }

    public void deleteAddressIfOrphan(long addressId) {
        jdbcTemplate.update(
                """
                DELETE FROM addresses
                 WHERE address_id = ?
                   AND NOT EXISTS (SELECT 1 FROM users WHERE users.address_id = addresses.address_id)
                   AND NOT EXISTS (SELECT 1 FROM students WHERE students.address_id = addresses.address_id)
                   AND NOT EXISTS (SELECT 1 FROM school_profiles WHERE school_profiles.address_id = addresses.address_id)
                """,
                addressId
        );
    }

    private boolean exists(String sql, Object value) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, value);
        return count != null && count > 0;
    }

    private int requiredReferenceId(String sql, String value) {
        Integer id = jdbcTemplate.queryForObject(sql, Integer.class, value);
        if (id == null) {
            throw new IllegalStateException("Required V3 reference data is missing: " + value);
        }
        return id;
    }

    private long requiredGeneratedKey(KeyHolder keyHolder, String entityName) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No generated key returned for " + entityName + ".");
        }
        return key.longValue();
    }

    private V3VerificationChallenge mapChallenge(ResultSet resultSet, int rowNumber) throws SQLException {
        return new V3VerificationChallenge(
                resultSet.getLong("verification_challenge_id"),
                resultSet.getString("challenge_uuid"),
                resultSet.getLong("user_id"),
                resultSet.getLong("address_id"),
                resultSet.getString("school_id"),
                resultSet.getString("email"),
                resultSet.getString("destination_masked"),
                resultSet.getString("code_hash"),
                resultSet.getString("challenge_status"),
                resultSet.getString("delivery_status"),
                resultSet.getInt("attempt_count"),
                resultSet.getInt("maximum_attempt_count"),
                resultSet.getInt("resend_count"),
                toInstant(resultSet.getTimestamp("next_resend_at")),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getTimestamp("user_created_at").toInstant(),
                resultSet.getString("account_status"),
                resultSet.getTimestamp("email_verified_at") != null
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String addressSource(V3TeacherRegistrationRequest.Address address) {
        boolean hasApiCodes = trimToNull(address.regionCode()) != null
                && trimToNull(address.cityMunicipalityCode()) != null
                && trimToNull(address.barangayCode()) != null;
        return hasApiCodes ? "api" : "manual";
    }
}
