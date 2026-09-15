package com.capstone.assessment.v3.account.repository;

import com.capstone.assessment.v3.account.model.V3TeacherAccount;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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

    public List<V3TeacherAccount> findVerifiedTeachers(String schoolId, String status) {
        return jdbcTemplate.query(
                TEACHER_SELECT + """
                         AND u.school_id = ?
                         AND status.status_name = ?
                         AND u.email_verified_at IS NOT NULL
                         ORDER BY u.last_name, u.first_name, u.middle_name, u.user_id
                        """,
                this::mapTeacher,
                schoolId,
                status
        );
    }

    public Optional<V3TeacherAccount> findVerifiedTeacher(String schoolId, long userId) {
        return jdbcTemplate.query(
                TEACHER_SELECT + """
                         AND u.school_id = ?
                         AND u.user_id = ?
                         AND u.email_verified_at IS NOT NULL
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
                   AND email_verified_at IS NOT NULL
                """,
                targetStatus,
                Timestamp.from(updatedAt),
                userId,
                schoolId,
                expectedStatus
        );
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
                )
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
