package com.capstone.assessment.importexport.repository;

import com.capstone.assessment.importexport.dto.ManualStudentRequest;
import com.capstone.assessment.importexport.dto.ManualStudentResponse;
import com.capstone.assessment.importexport.dto.ManualStudentUpdateRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class ManualStudentRepository {

    private final JdbcTemplate jdbcTemplate;

    public ManualStudentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long createStudent(ManualStudentRequest request) {
        String sql = """
                INSERT INTO student (student_lrn, first_name, last_name, gender)
                VALUES (?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setString(1, request.studentLrn());
            preparedStatement.setString(2, request.firstName());
            preparedStatement.setString(3, request.lastName());
            preparedStatement.setString(4, request.gender());
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated student_id").longValue();
    }

    public void updateStudent(Long studentId, ManualStudentUpdateRequest request) {
        String studentSql = """
                UPDATE student
                SET student_lrn = ?,
                    first_name = ?,
                    last_name = ?,
                    gender = ?
                WHERE student_id = ?
                """;

        String enrollmentSql = """
                UPDATE student_enrollment
                SET section_id = ?,
                    academic_year_id = ?
                WHERE student_enrollment_id = (
                    SELECT latest.student_enrollment_id
                    FROM (
                        SELECT MAX(student_enrollment_id) AS student_enrollment_id
                        FROM student_enrollment
                        WHERE student_id = ?
                    ) latest
                )
                """;

        jdbcTemplate.update(
                studentSql,
                request.studentLrn(),
                request.firstName(),
                request.lastName(),
                request.gender(),
                studentId
        );

        jdbcTemplate.update(
                enrollmentSql,
                request.sectionId(),
                request.academicYearId(),
                studentId
        );
    }

    public void enrollStudent(Long studentId, Long sectionId, Long academicYearId) {
        String sql = """
                INSERT INTO student_enrollment (student_id, section_id, academic_year_id)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(sql, studentId, sectionId, academicYearId);
    }

    public boolean studentExistsByLrn(String studentLrn) {
        String sql = """
                SELECT COUNT(*)
                FROM student
                WHERE student_lrn = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, studentLrn);
        return count != null && count > 0;
    }

    public boolean studentExistsById(Long studentId) {
        String sql = """
                SELECT COUNT(*)
                FROM student
                WHERE student_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, studentId);
        return count != null && count > 0;
    }

    public boolean enrollmentExists(Long studentId, Long sectionId, Long academicYearId) {
        String sql = """
                SELECT COUNT(*)
                FROM student_enrollment
                WHERE student_id = ?
                  AND section_id = ?
                  AND academic_year_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, studentId, sectionId, academicYearId);
        return count != null && count > 0;
    }

    public boolean sectionExists(Long sectionId) {
        String sql = """
                SELECT COUNT(*)
                FROM section
                WHERE section_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, sectionId);
        return count != null && count > 0;
    }

    public boolean academicYearExists(Long academicYearId) {
        String sql = """
                SELECT COUNT(*)
                FROM academic_year
                WHERE academic_year_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, academicYearId);
        return count != null && count > 0;
    }

    public Optional<ManualStudentResponse> findStudentById(Long studentId) {
        String sql = """
                SELECT
                    st.student_id,
                    st.student_lrn,
                    st.first_name,
                    st.last_name,
                    st.gender,
                    sec.section_id,
                    sec.section_name,
                    gl.grade_level_id,
                    gl.grade_level_name,
                    ay.academic_year_id,
                    ay.year_name AS academic_year
                FROM student st
                LEFT JOIN (
                    SELECT student_id, MAX(student_enrollment_id) AS student_enrollment_id
                    FROM student_enrollment
                    GROUP BY student_id
                ) latest_enrollment
                    ON latest_enrollment.student_id = st.student_id
                LEFT JOIN student_enrollment se
                    ON se.student_enrollment_id = latest_enrollment.student_enrollment_id
                LEFT JOIN section sec
                    ON sec.section_id = se.section_id
                LEFT JOIN grade_level gl
                    ON gl.grade_level_id = sec.grade_level_id
                LEFT JOIN academic_year ay
                    ON ay.academic_year_id = se.academic_year_id
                WHERE st.student_id = ?
                """;

        List<ManualStudentResponse> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapManualStudentResponse(rs),
                studentId
        );

        return results.stream().findFirst();
    }

    public List<ManualStudentResponse> findAllStudents() {
        String sql = """
                SELECT
                    st.student_id,
                    st.student_lrn,
                    st.first_name,
                    st.last_name,
                    st.gender,
                    sec.section_id,
                    sec.section_name,
                    gl.grade_level_id,
                    gl.grade_level_name,
                    ay.academic_year_id,
                    ay.year_name AS academic_year
                FROM student st
                LEFT JOIN (
                    SELECT student_id, MAX(student_enrollment_id) AS student_enrollment_id
                    FROM student_enrollment
                    GROUP BY student_id
                ) latest_enrollment
                    ON latest_enrollment.student_id = st.student_id
                LEFT JOIN student_enrollment se
                    ON se.student_enrollment_id = latest_enrollment.student_enrollment_id
                LEFT JOIN section sec
                    ON sec.section_id = se.section_id
                LEFT JOIN grade_level gl
                    ON gl.grade_level_id = sec.grade_level_id
                LEFT JOIN academic_year ay
                    ON ay.academic_year_id = se.academic_year_id
                ORDER BY st.last_name, st.first_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapManualStudentResponse(rs));
    }

    private ManualStudentResponse mapManualStudentResponse(ResultSet rs) throws SQLException {
        return new ManualStudentResponse(
                rs.getLong("student_id"),
                rs.getString("student_lrn"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("gender"),
                getNullableLong(rs, "section_id"),
                rs.getString("section_name"),
                getNullableLong(rs, "grade_level_id"),
                rs.getString("grade_level_name"),
                getNullableLong(rs, "academic_year_id"),
                rs.getString("academic_year")
        );
    }

    private Long getNullableLong(ResultSet rs, String columnLabel) throws SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }
}
