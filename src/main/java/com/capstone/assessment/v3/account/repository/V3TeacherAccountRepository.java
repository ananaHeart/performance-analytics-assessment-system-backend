package com.capstone.assessment.v3.account.repository;

import com.capstone.assessment.v3.account.model.V3TeacherAccount;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3TeacherAccountRepository {

    private static final String TEACHER_SELECT = """
            SELECT u.user_id,
                   u.school_id,
                   u.address_id,
                   u.gender_id,
                   gender.gender_name,
                   u.major_id,
                   major.major_name,
                   u.educational_attainment_id,
                   attainment.attainment_name AS educational_attainment_name,
                   u.suffix_id,
                   suffix.suffix_name,
                   u.first_name,
                   u.middle_name,
                   u.last_name,
                   u.birth_date,
                   u.teaching_start_month,
                   u.teaching_start_year,
                   u.email,
                   u.contact_number,
                   role.role_name,
                   status.status_name,
                   u.email_verified_at,
                   u.contact_verified_at,
                   u.contract_end_date,
                   u.archived_at,
                   u.created_at,
                   u.updated_at,
                   address.country_code,
                   address.region_code,
                   address.region_name,
                   address.province_code,
                   address.province_name,
                   address.city_municipality_code,
                   address.city_municipality_name,
                   address.barangay_code,
                   address.barangay_name,
                   address.address_line,
                   address.postal_code,
                   address.address_source
              FROM users u
              JOIN roles role ON role.role_id = u.role_id
              JOIN statuses status ON status.status_id = u.status_id
              JOIN genders gender ON gender.gender_id = u.gender_id
              JOIN addresses address ON address.address_id = u.address_id
              LEFT JOIN majors major ON major.major_id = u.major_id
              LEFT JOIN educational_attainments attainment
                ON attainment.educational_attainment_id = u.educational_attainment_id
              LEFT JOIN suffixes suffix ON suffix.suffix_id = u.suffix_id
             WHERE role.role_name = 'teacher'
            """;

    private final JdbcTemplate jdbcTemplate;

    public V3TeacherAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<V3TeacherAccount> findVerifiedTeachers(String schoolId, List<String> statuses) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(statuses.size(), "?"));
        List<Object> arguments = new java.util.ArrayList<>();
        arguments.add(schoolId);
        arguments.addAll(statuses);
        return jdbcTemplate.query(
                TEACHER_SELECT + """
                         AND u.school_id = ?
                         AND status.status_name IN (%s)
                         AND (u.email_verified_at IS NOT NULL OR u.contact_verified_at IS NOT NULL)
                         ORDER BY u.last_name, u.first_name, u.middle_name, u.user_id
                        """.formatted(placeholders),
                this::mapTeacher,
                arguments.toArray()
        );
    }

    public Optional<V3TeacherAccount> findVerifiedTeacher(String schoolId, long userId) {
        return jdbcTemplate.query(
                TEACHER_SELECT + """
                         AND u.school_id = ?
                         AND u.user_id = ?
                         AND (u.email_verified_at IS NOT NULL OR u.contact_verified_at IS NOT NULL)
                        """,
                this::mapTeacher,
                schoolId,
                userId
        ).stream().findFirst();
    }

    public Optional<V3TeacherAccount> lockTeacher(String schoolId, long userId) {
        return jdbcTemplate.query(
                TEACHER_SELECT + """
                         AND u.school_id = ?
                         AND u.user_id = ?
                         FOR UPDATE
                        """,
                this::mapTeacher,
                schoolId,
                userId
        ).stream().findFirst();
    }

    public int transitionStatus(
            String schoolId,
            long userId,
            String expectedStatus,
            String targetStatus,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                UPDATE users
                   SET status_id = (
                           SELECT status_id
                             FROM statuses
                            WHERE status_name = ?
                              AND is_active = TRUE
                       ),
                       updated_at = ?
                 WHERE user_id = ?
                   AND school_id = ?
                   AND role_id = (
                           SELECT role_id
                             FROM roles
                            WHERE role_name = 'teacher'
                       )
                   AND status_id = (
                           SELECT status_id
                             FROM statuses
                            WHERE status_name = ?
                              AND is_active = TRUE
                       )
                   AND (email_verified_at IS NOT NULL OR contact_verified_at IS NOT NULL)
                """,
                targetStatus,
                Timestamp.from(updatedAt),
                userId,
                schoolId,
                expectedStatus
        );
    }

    /** REG-2: the last day the teacher's contract is valid. */
    public int updateContractEndDate(long userId, LocalDate contractEndDate, Instant updatedAt) {
        return jdbcTemplate.update(
                "UPDATE users SET contract_end_date = ?, updated_at = ? WHERE user_id = ?",
                contractEndDate,
                Timestamp.from(updatedAt),
                userId
        );
    }

    /** TCH-4: archive or restore; archived_at is set only while the account is archived. */
    public int transitionArchiveState(
            String schoolId,
            long userId,
            String expectedStatus,
            String targetStatus,
            Instant archivedAt,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                UPDATE users
                   SET status_id = (SELECT status_id FROM statuses WHERE status_name = ? AND is_active = TRUE),
                       archived_at = ?,
                       updated_at = ?
                 WHERE user_id = ?
                   AND school_id = ?
                   AND status_id = (SELECT status_id FROM statuses WHERE status_name = ? AND is_active = TRUE)
                """,
                targetStatus,
                archivedAt == null ? null : Timestamp.from(archivedAt),
                Timestamp.from(updatedAt),
                userId,
                schoolId,
                expectedStatus
        );
    }

    /** TCH-9: the teacher's open class assignments end, so the class and subject can be reassigned. */
    public int endOpenClassAssignments(long teacherUserId, long principalUserId, String reason, Instant endedAt) {
        return jdbcTemplate.update(
                """
                UPDATE class_assignments
                   SET status = 'archived', ended_at = ?, status_changed_by_user_id = ?, status_reason = ?
                 WHERE user_id = ?
                   AND status IN ('active', 'inactive')
                """,
                Timestamp.from(endedAt),
                principalUserId,
                reason,
                teacherUserId
        );
    }

    /** Signs the teacher out everywhere (TCH-5 rejection, archive, inactive). */
    public int revokeSessions(long userId, Instant revokedAt) {
        return jdbcTemplate.update(
                "UPDATE auth_sessions SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL",
                Timestamp.from(revokedAt),
                userId
        );
    }

    /** REG-2 (D2): approved teachers whose contract has ended become inactive, never deleted. */
    public List<Long> findActiveTeachersWithEndedContracts(LocalDate today) {
        return jdbcTemplate.queryForList(
                """
                SELECT u.user_id
                  FROM users u
                  JOIN roles role ON role.role_id = u.role_id AND role.role_name = 'teacher'
                  JOIN statuses status ON status.status_id = u.status_id AND status.status_name = 'active'
                 WHERE u.contract_end_date < ?
                """,
                Long.class,
                today
        );
    }

    public int deactivateEndedContract(long userId, LocalDate today, Instant updatedAt) {
        return jdbcTemplate.update(
                """
                UPDATE users
                   SET status_id = (SELECT status_id FROM statuses WHERE status_name = 'inactive' AND is_active = TRUE),
                       updated_at = ?
                 WHERE user_id = ?
                   AND contract_end_date < ?
                   AND status_id = (SELECT status_id FROM statuses WHERE status_name = 'active' AND is_active = TRUE)
                """,
                Timestamp.from(updatedAt),
                userId,
                today
        );
    }

    /** TCH-4: the principal corrects the teacher's details (not email, contact or password). */
    public int updateTeacherDetails(long userId, TeacherDetails details, Instant updatedAt) {
        return jdbcTemplate.update(
                """
                UPDATE users
                   SET first_name = ?, middle_name = ?, last_name = ?, suffix_id = ?, gender_id = ?,
                       birth_date = ?, major_id = ?, educational_attainment_id = ?,
                       teaching_start_month = ?, teaching_start_year = ?, updated_at = ?
                 WHERE user_id = ?
                """,
                details.firstName(),
                details.middleName(),
                details.lastName(),
                details.suffixId(),
                details.genderId(),
                details.birthDate(),
                details.majorId(),
                details.educationalAttainmentId(),
                details.teachingStartMonth(),
                details.teachingStartYear(),
                Timestamp.from(updatedAt),
                userId
        );
    }

    public int updateAddress(long addressId, V3TeacherAccount.Address address) {
        return jdbcTemplate.update(
                """
                UPDATE addresses
                   SET country_code = ?, region_code = ?, region_name = ?, province_code = ?,
                       province_name = ?, city_municipality_code = ?, city_municipality_name = ?,
                       barangay_code = ?, barangay_name = ?, address_line = ?, postal_code = ?
                 WHERE address_id = ?
                """,
                address.countryCode(),
                address.regionCode(),
                address.regionName(),
                address.provinceCode(),
                address.provinceName(),
                address.cityMunicipalityCode(),
                address.cityMunicipalityName(),
                address.barangayCode(),
                address.barangayName(),
                address.addressLine(),
                address.postalCode(),
                addressId
        );
    }

    public record TeacherDetails(
            String firstName,
            String middleName,
            String lastName,
            Integer suffixId,
            int genderId,
            LocalDate birthDate,
            int majorId,
            int educationalAttainmentId,
            int teachingStartMonth,
            int teachingStartYear
    ) {
    }

    private V3TeacherAccount mapTeacher(ResultSet resultSet, int rowNumber) throws SQLException {
        return new V3TeacherAccount(
                resultSet.getLong("user_id"),
                resultSet.getString("school_id"),
                resultSet.getLong("address_id"),
                resultSet.getInt("gender_id"),
                resultSet.getString("gender_name"),
                nullableInteger(resultSet, "major_id"),
                resultSet.getString("major_name"),
                nullableInteger(resultSet, "educational_attainment_id"),
                resultSet.getString("educational_attainment_name"),
                nullableInteger(resultSet, "suffix_id"),
                resultSet.getString("suffix_name"),
                resultSet.getString("first_name"),
                resultSet.getString("middle_name"),
                resultSet.getString("last_name"),
                resultSet.getObject("birth_date", java.time.LocalDate.class),
                nullableInteger(resultSet, "teaching_start_month"),
                nullableInteger(resultSet, "teaching_start_year"),
                resultSet.getString("email"),
                resultSet.getString("contact_number"),
                resultSet.getString("role_name"),
                resultSet.getString("status_name"),
                nullableInstant(resultSet, "email_verified_at"),
                nullableInstant(resultSet, "contact_verified_at"),
                requiredInstant(resultSet, "created_at"),
                requiredInstant(resultSet, "updated_at"),
                new V3TeacherAccount.Address(
                        resultSet.getString("country_code"),
                        resultSet.getString("region_code"),
                        resultSet.getString("region_name"),
                        resultSet.getString("province_code"),
                        resultSet.getString("province_name"),
                        resultSet.getString("city_municipality_code"),
                        resultSet.getString("city_municipality_name"),
                        resultSet.getString("barangay_code"),
                        resultSet.getString("barangay_name"),
                        resultSet.getString("address_line"),
                        resultSet.getString("postal_code"),
                        resultSet.getString("address_source")
                ),
                resultSet.getObject("contract_end_date", LocalDate.class),
                nullableInstant(resultSet, "archived_at")
        );
    }

    private Integer nullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private Instant nullableInstant(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp value = resultSet.getTimestamp(columnName);
        return value == null ? null : value.toInstant();
    }

    private Instant requiredInstant(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getTimestamp(columnName).toInstant();
    }
}
