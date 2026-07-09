package com.capstone.assessment.schoolsetup.repository;

import com.capstone.assessment.schoolsetup.dto.ClassAssignmentDto;
import com.capstone.assessment.schoolsetup.dto.CreateClassAssignmentRequest;
import com.capstone.assessment.schoolsetup.dto.CreateSectionRequest;
import com.capstone.assessment.schoolsetup.dto.GradeLevelDto;
import com.capstone.assessment.schoolsetup.dto.SectionDto;
import com.capstone.assessment.schoolsetup.dto.StudentDto;
import com.capstone.assessment.schoolsetup.dto.SubjectDto;
import com.capstone.assessment.schoolsetup.dto.TeacherDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

@Repository
public class SchoolSetupRepository {

    private final JdbcTemplate jdbcTemplate;

    public SchoolSetupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GradeLevelDto> findAllGradeLevels() {
        String sql = """
                SELECT grade_level_id,
                       grade_level_name
                FROM grade_level
                ORDER BY grade_level_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new GradeLevelDto(
                rs.getLong("grade_level_id"),
                rs.getString("grade_level_name")
        ));
    }

    public List<SubjectDto> findAllSubjects() {
        String sql = """
                SELECT subject_id,
                       subject_code,
                       subject_name
                FROM subject
                ORDER BY subject_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new SubjectDto(
                rs.getLong("subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name")
        ));
    }

    public List<SectionDto> findAllSections() {
        String sql = """
                SELECT s.section_id,
                       s.grade_level_id,
                       gl.grade_level_name,
                       s.section_name
                FROM section s
                JOIN grade_level gl ON gl.grade_level_id = s.grade_level_id
                ORDER BY s.grade_level_id, s.section_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new SectionDto(
                rs.getLong("section_id"),
                rs.getLong("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getString("section_name")
        ));
    }

    public List<TeacherDto> findAllTeachers() {
        String sql = """
                SELECT user_id,
                       first_name,
                       last_name,
                       email,
                       status
                FROM `user`
                WHERE role = 'teacher'
                ORDER BY last_name, first_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TeacherDto(
                rs.getLong("user_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("status")
        ));
    }

    public List<StudentDto> findAllStudents() {
        String sql = """
                SELECT st.student_id,
                       st.student_lrn,
                       st.first_name,
                       st.last_name,
                       st.gender,
                       sec.section_id,
                       sec.section_name,
                       gl.grade_level_id,
                       gl.grade_level_name
                FROM student st
                JOIN student_enrollment se ON se.student_id = st.student_id
                JOIN section sec ON sec.section_id = se.section_id
                JOIN grade_level gl ON gl.grade_level_id = sec.grade_level_id
                ORDER BY st.last_name, st.first_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new StudentDto(
                rs.getLong("student_id"),
                rs.getString("student_lrn"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("gender"),
                rs.getLong("section_id"),
                rs.getString("section_name"),
                rs.getLong("grade_level_id"),
                rs.getString("grade_level_name")
        ));
    }

    public List<ClassAssignmentDto> findAllClassAssignments() {
        String sql = """
                SELECT c.class_id,
                       u.user_id AS teacher_id,
                       CONCAT(u.first_name, ' ', u.last_name) AS teacher_name,
                       s.subject_id,
                       s.subject_name,
                       sec.section_id,
                       sec.section_name,
                       gl.grade_level_id,
                       gl.grade_level_name,
                       ay.year_name AS academic_year
                FROM `class` c
                JOIN `user` u ON u.user_id = c.user_id
                JOIN subject s ON s.subject_id = c.subject_id
                JOIN section sec ON sec.section_id = c.section_id
                JOIN grade_level gl ON gl.grade_level_id = sec.grade_level_id
                JOIN academic_year ay ON ay.academic_year_id = c.academic_year_id
                ORDER BY ay.year_name, gl.grade_level_id, sec.section_name, s.subject_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ClassAssignmentDto(
                rs.getLong("class_id"),
                rs.getLong("teacher_id"),
                rs.getString("teacher_name"),
                rs.getLong("subject_id"),
                rs.getString("subject_name"),
                rs.getLong("section_id"),
                rs.getString("section_name"),
                rs.getLong("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getString("academic_year")
        ));
    }

    public Long createSection(CreateSectionRequest request) {
        String sql = """
                INSERT INTO section (grade_level_id, section_name)
                VALUES (?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setLong(1, request.gradeLevelId());
            preparedStatement.setString(2, request.sectionName());
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated section_id").longValue();
    }

    public Long createClassAssignment(CreateClassAssignmentRequest request) {
        String sql = """
                INSERT INTO `class` (academic_year_id, user_id, subject_id, section_id)
                VALUES (?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setLong(1, request.academicYearId());
            preparedStatement.setLong(2, request.teacherId());
            preparedStatement.setLong(3, request.subjectId());
            preparedStatement.setLong(4, request.sectionId());
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated class_id").longValue();
    }

    public boolean sectionExists(Long gradeLevelId, String sectionName) {
        String sql = """
                SELECT COUNT(*)
                FROM section
                WHERE grade_level_id = ?
                  AND LOWER(section_name) = LOWER(?)
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, gradeLevelId, sectionName);
        return count != null && count > 0;
    }

    public boolean classAssignmentExists(Long academicYearId, Long teacherId, Long subjectId, Long sectionId) {
        String sql = """
                SELECT COUNT(*)
                FROM `class`
                WHERE academic_year_id = ?
                  AND user_id = ?
                  AND subject_id = ?
                  AND section_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                academicYearId,
                teacherId,
                subjectId,
                sectionId
        );
        return count != null && count > 0;
    }
}
