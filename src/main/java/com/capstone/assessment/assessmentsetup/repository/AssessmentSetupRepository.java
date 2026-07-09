package com.capstone.assessment.assessmentsetup.repository;

import com.capstone.assessment.assessmentsetup.dto.AssessmentDto;
import com.capstone.assessment.assessmentsetup.dto.CompetencyOptionDto;
import com.capstone.assessment.assessmentsetup.dto.CreateAssessmentRequest;
import com.capstone.assessment.assessmentsetup.dto.CreateTestPartRequest;
import com.capstone.assessment.assessmentsetup.dto.TestPartDetailDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AssessmentSetupRepository {

    private final JdbcTemplate jdbcTemplate;

    public AssessmentSetupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AssessmentDto> findAssessmentsByTeacherId(Long teacherId) {
        String sql = """
                SELECT t.test_id,
                       t.class_id,
                       t.test_name,
                       t.test_type,
                       t.test_date,
                       t.grading_period_id,
                       gp.period_name AS grading_period_name,
                       t.test_status,
                       s.subject_name,
                       sec.section_name,
                       gl.grade_level_name
                FROM `test` t
                JOIN `class` c ON c.class_id = t.class_id
                JOIN subject s ON s.subject_id = c.subject_id
                JOIN section sec ON sec.section_id = c.section_id
                JOIN grade_level gl ON gl.grade_level_id = sec.grade_level_id
                LEFT JOIN grading_period gp ON gp.grading_period_id = t.grading_period_id
                WHERE c.user_id = ?
                ORDER BY t.test_date DESC, t.test_id DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new AssessmentDto(
                rs.getLong("test_id"),
                rs.getLong("class_id"),
                rs.getString("test_name"),
                rs.getString("test_type"),
                rs.getObject("test_date", Date.class).toLocalDate(),
                getNullableLong(rs, "grading_period_id"),
                rs.getString("grading_period_name"),
                rs.getString("test_status"),
                rs.getString("subject_name"),
                rs.getString("section_name"),
                rs.getString("grade_level_name")
        ), teacherId);
    }

    public Optional<AssessmentDto> findAssessmentById(Long testId) {
        String sql = """
                SELECT t.test_id,
                       t.class_id,
                       t.test_name,
                       t.test_type,
                       t.test_date,
                       t.grading_period_id,
                       gp.period_name AS grading_period_name,
                       t.test_status,
                       s.subject_name,
                       sec.section_name,
                       gl.grade_level_name
                FROM `test` t
                JOIN `class` c ON c.class_id = t.class_id
                JOIN subject s ON s.subject_id = c.subject_id
                JOIN section sec ON sec.section_id = c.section_id
                JOIN grade_level gl ON gl.grade_level_id = sec.grade_level_id
                LEFT JOIN grading_period gp ON gp.grading_period_id = t.grading_period_id
                WHERE t.test_id = ?
                """;

        List<AssessmentDto> results = jdbcTemplate.query(sql, (rs, rowNum) -> new AssessmentDto(
                rs.getLong("test_id"),
                rs.getLong("class_id"),
                rs.getString("test_name"),
                rs.getString("test_type"),
                rs.getObject("test_date", Date.class).toLocalDate(),
                getNullableLong(rs, "grading_period_id"),
                rs.getString("grading_period_name"),
                rs.getString("test_status"),
                rs.getString("subject_name"),
                rs.getString("section_name"),
                rs.getString("grade_level_name")
        ), testId);

        return results.stream().findFirst();
    }

    public List<TestPartDetailDto> findTestPartsByTestId(Long testId) {
        String sql = """
                SELECT tp.test_part_id,
                       tp.test_id,
                       tp.competency_id,
                       ct.competency_name,
                       tp.part_order,
                       tp.part_type,
                       tp.number_of_items,
                       tp.points_per_item,
                       tp.answer_key
                FROM test_part tp
                JOIN competency_tags ct ON ct.competency_id = tp.competency_id
                WHERE tp.test_id = ?
                ORDER BY tp.test_part_id ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TestPartDetailDto(
                rs.getLong("test_part_id"),
                rs.getLong("test_id"),
                rs.getLong("competency_id"),
                rs.getString("competency_name"),
                rs.getString("part_order"),
                rs.getString("part_type"),
                rs.getInt("number_of_items"),
                rs.getInt("points_per_item"),
                rs.getString("answer_key")
        ), testId);
    }

    public Long createAssessment(CreateAssessmentRequest request) {
        String sql = """
                INSERT INTO `test` (class_id, test_name, test_type, test_date, grading_period_id, test_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setLong(1, request.classId());
            preparedStatement.setString(2, request.testName());
            preparedStatement.setString(3, request.testType());
            preparedStatement.setObject(4, request.testDate());
            if (request.gradingPeriodId() == null) {
                preparedStatement.setObject(5, null);
            } else {
                preparedStatement.setLong(5, request.gradingPeriodId());
            }
            preparedStatement.setString(6, request.testStatus());
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated test_id").longValue();
    }

    public Long createTestPart(Long testId, CreateTestPartRequest request) {
        String sql = """
                INSERT INTO test_part (
                    test_id,
                    competency_id,
                    part_order,
                    part_type,
                    number_of_items,
                    points_per_item,
                    answer_key
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setLong(1, testId);
            preparedStatement.setLong(2, request.competencyId());
            preparedStatement.setString(3, request.partOrder());
            preparedStatement.setString(4, request.partType());
            preparedStatement.setInt(5, request.numberOfItems());
            preparedStatement.setInt(6, request.pointsPerItem());
            preparedStatement.setString(7, request.answerKey());
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated test_part_id").longValue();
    }

    public boolean testExists(Long testId) {
        String sql = """
                SELECT COUNT(*)
                FROM `test`
                WHERE test_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, testId);
        return count != null && count > 0;
    }

    public boolean classExists(Long classId) {
        String sql = """
                SELECT COUNT(*)
                FROM `class`
                WHERE class_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, classId);
        return count != null && count > 0;
    }

    public boolean gradingPeriodMatchesClassAcademicYear(Long classId, Long gradingPeriodId) {
        String sql = """
                SELECT COUNT(*)
                FROM grading_period gp
                JOIN `class` c ON c.academic_year_id = gp.academic_year_id
                WHERE c.class_id = ?
                  AND gp.grading_period_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, classId, gradingPeriodId);
        return count != null && count > 0;
    }

    public boolean competencyExists(Long competencyId) {
        String sql = """
                SELECT COUNT(*)
                FROM competency_tags
                WHERE competency_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, competencyId);
        return count != null && count > 0;
    }

    public boolean testPartOrderExists(Long testId, String partOrder) {
        String sql = """
                SELECT COUNT(*)
                FROM test_part
                WHERE test_id = ?
                  AND LOWER(part_order) = LOWER(?)
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, testId, partOrder);
        return count != null && count > 0;
    }

    public List<CompetencyOptionDto> findCompetenciesForClass(Long classId) {
        String sql = """
                SELECT ct.competency_id,
                       ct.grade_level_id,
                       ct.subject_id,
                       ct.competency_name
                FROM `class` c
                JOIN section sec ON sec.section_id = c.section_id
                JOIN grade_level gl ON gl.grade_level_id = sec.grade_level_id
                JOIN competency_tags ct
                  ON ct.grade_level_id = gl.grade_level_id
                 AND ct.subject_id = c.subject_id
                WHERE c.class_id = ?
                ORDER BY ct.competency_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CompetencyOptionDto(
                rs.getLong("competency_id"),
                rs.getLong("grade_level_id"),
                rs.getLong("subject_id"),
                rs.getString("competency_name")
        ), classId);
    }

    private Long getNullableLong(java.sql.ResultSet rs, String columnLabel) throws java.sql.SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }
}
