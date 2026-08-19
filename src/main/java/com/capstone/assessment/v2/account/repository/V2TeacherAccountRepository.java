package com.capstone.assessment.v2.account.repository;

import com.capstone.assessment.v2.account.dto.V2AddressRequest;
import com.capstone.assessment.v2.account.dto.V2CreateTeacherRequest;
import com.capstone.assessment.v2.account.dto.V2EducationalAttainmentOption;
import com.capstone.assessment.v2.account.dto.V2GenderOption;
import com.capstone.assessment.v2.account.dto.V2MajorOption;
import com.capstone.assessment.v2.account.dto.V2SchoolOption;
import com.capstone.assessment.v2.account.model.V2TeacherAccount;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Profile("v2")
@Repository
public class V2TeacherAccountRepository {

    private static final String TEACHER_SELECT = """
            SELECT u.user_id,
                   u.school_id,
                   u.address_id,
                   u.gender_id,
                   u.major_id,
                   u.educational_attainment_id,
                   u.first_name,
                   u.middle_name,
                   u.last_name,
                   u.suffix,
                   u.birth_date,
                   u.teaching_start_date,
                   u.email,
                   u.contact_number,
                   r.role_name,
                   s.status_name,
                   u.email_verified_at,
                   u.contact_verified_at,
                   u.created_at,
                   u.updated_at
              FROM users u
              JOIN roles r ON r.role_id = u.role_id
              JOIN statuses s ON s.status_id = u.status_id
             WHERE r.role_name = 'teacher'
            """;

    private final JdbcTemplate jdbcTemplate;

    public V2TeacherAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

    public boolean educationalAttainmentExists(Integer attainmentId) {
        return exists(
                "SELECT COUNT(*) FROM educational_attainments WHERE educational_attainment_id = ? AND is_active = TRUE",
                attainmentId
        );
    }

    public Optional<String> findSchoolIdByCode(String schoolCode) {
        return jdbcTemplate.query(
                """
                SELECT school_id
                  FROM school_profiles
                 WHERE LOWER(school_id) = LOWER(?)
                """,
                (resultSet, rowNumber) -> resultSet.getString("school_id"),
                schoolCode
        ).stream().findFirst();
    }

    public List<V2GenderOption> listGenders() {
        return jdbcTemplate.query(
                """
                SELECT gender_id,
                       gender_name
                  FROM genders
                 ORDER BY gender_name
                """,
                (resultSet, rowNumber) -> new V2GenderOption(
                        resultSet.getInt("gender_id"),
                        resultSet.getString("gender_name")
                )
        );
    }

    public List<V2MajorOption> listMajors() {
        return jdbcTemplate.query(
                """
                SELECT major_id,
                       major_name
                  FROM majors
                 ORDER BY major_name
                """,
                (resultSet, rowNumber) -> new V2MajorOption(
                        resultSet.getInt("major_id"),
                        resultSet.getString("major_name")
                )
        );
    }

    public List<V2EducationalAttainmentOption> listActiveEducationalAttainments() {
        return jdbcTemplate.query(
                """
                SELECT educational_attainment_id,
                       attainment_name
                  FROM educational_attainments
                 WHERE is_active = TRUE
                 ORDER BY attainment_order, attainment_name
                """,
                (resultSet, rowNumber) -> new V2EducationalAttainmentOption(
                        resultSet.getInt("educational_attainment_id"),
                        resultSet.getString("attainment_name")
                )
        );
    }

    public List<V2SchoolOption> listSchools() {
        return jdbcTemplate.query(
                """
                SELECT school_id,
                       school_name
                  FROM school_profiles
                 ORDER BY school_name, school_id
                """,
                (resultSet, rowNumber) -> new V2SchoolOption(
                        resultSet.getString("school_id"),
                        resultSet.getString("school_name")
                )
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

    public long insertAddress(V2AddressRequest address) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO addresses (
                        country_code, region_code, region_name, province_code, province_name,
                        city_municipality_code, city_municipality_name, barangay_code,
                        barangay_name, address_line, postal_code, address_source
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'manual')
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
            return statement;
        }, keyHolder);
        return requiredGeneratedKey(keyHolder, "address");
    }

    public long insertTeacher(
            String schoolId,
            long addressId,
            int teacherRoleId,
            int pendingStatusId,
            V2CreateTeacherRequest request,
            String normalizedEmail,
            String normalizedContact,
            String passwordHash,
            Instant createdAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO users (
                        school_id, address_id, gender_id, major_id, educational_attainment_id,
                        role_id, status_id, first_name, middle_name, last_name, suffix,
                        birth_date, teaching_start_date, email, contact_number, password_hash,
                        password_changed_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, schoolId);
            statement.setLong(2, addressId);
            statement.setInt(3, request.genderId());
            statement.setInt(4, request.majorId());
            statement.setInt(5, request.educationalAttainmentId());
            statement.setInt(6, teacherRoleId);
            statement.setInt(7, pendingStatusId);
            statement.setString(8, request.firstName().trim());
            statement.setString(9, trimToNull(request.middleName()));
            statement.setString(10, request.lastName().trim());
            statement.setString(11, trimToNull(request.suffix()));
            statement.setDate(12, request.birthDate() == null ? null : Date.valueOf(request.birthDate()));
            statement.setDate(13, request.teachingStartDate() == null ? null : Date.valueOf(request.teachingStartDate()));
            statement.setString(14, normalizedEmail);
            statement.setString(15, normalizedContact);
            statement.setString(16, passwordHash);
            statement.setTimestamp(17, Timestamp.from(createdAt));
            statement.setTimestamp(18, Timestamp.from(createdAt));
            statement.setTimestamp(19, Timestamp.from(createdAt));
            return statement;
        }, keyHolder);
        return requiredGeneratedKey(keyHolder, "teacher account");
    }

    public Optional<V2TeacherAccount> findTeacher(String schoolId, long userId) {
        return jdbcTemplate.query(
                TEACHER_SELECT + " AND u.school_id = ? AND u.user_id = ?",
                this::mapTeacher,
                schoolId,
                userId
        ).stream().findFirst();
    }

    public List<V2TeacherAccount> findTeachers(String schoolId, String status) {
        if (status == null) {
            return jdbcTemplate.query(
                    TEACHER_SELECT + " AND u.school_id = ? ORDER BY u.last_name, u.first_name, u.user_id",
                    this::mapTeacher,
                    schoolId
            );
        }
        return jdbcTemplate.query(
                TEACHER_SELECT + " AND u.school_id = ? AND s.status_name = ? ORDER BY u.last_name, u.first_name, u.user_id",
                this::mapTeacher,
                schoolId,
                status
        );
    }

    public int transitionStatus(
            String schoolId,
            long userId,
            int teacherRoleId,
            int pendingStatusId,
            int targetStatusId
    ) {
        return jdbcTemplate.update(
                """
                UPDATE users
                   SET status_id = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                   AND school_id = ?
                   AND role_id = ?
                   AND status_id = ?
                """,
                targetStatusId,
                userId,
                schoolId,
                teacherRoleId,
                pendingStatusId
        );
    }

    private boolean exists(String sql, Object value) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, value);
        return count != null && count > 0;
    }

    private int requiredReferenceId(String sql, String value) {
        Integer id = jdbcTemplate.queryForObject(sql, Integer.class, value);
        if (id == null) {
            throw new IllegalStateException("Required V2 reference data is missing: " + value);
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

    private V2TeacherAccount mapTeacher(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new V2TeacherAccount(
                resultSet.getLong("user_id"),
                resultSet.getString("school_id"),
                resultSet.getLong("address_id"),
                resultSet.getInt("gender_id"),
                nullableInteger(resultSet, "major_id"),
                nullableInteger(resultSet, "educational_attainment_id"),
                resultSet.getString("first_name"),
                resultSet.getString("middle_name"),
                resultSet.getString("last_name"),
                resultSet.getString("suffix"),
                resultSet.getObject("birth_date", java.time.LocalDate.class),
                resultSet.getObject("teaching_start_date", java.time.LocalDate.class),
                resultSet.getString("email"),
                resultSet.getString("contact_number"),
                resultSet.getString("role_name"),
                resultSet.getString("status_name"),
                resultSet.getTimestamp("email_verified_at") != null,
                resultSet.getTimestamp("contact_verified_at") != null,
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private Integer nullableInteger(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
