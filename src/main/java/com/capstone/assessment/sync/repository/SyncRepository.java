package com.capstone.assessment.sync.repository;

import com.capstone.assessment.sync.dto.ClassSyncDto;
import com.capstone.assessment.sync.dto.CompetencySyncDto;
import com.capstone.assessment.sync.dto.ItemResponseSyncDto;
import com.capstone.assessment.sync.dto.StudentSyncDto;
import com.capstone.assessment.sync.dto.TestResultSyncDto;
import com.capstone.assessment.sync.dto.TestPartSyncDto;
import com.capstone.assessment.sync.dto.TestSyncDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class SyncRepository {

    private final JdbcTemplate jdbcTemplate;

    public SyncRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ClassSyncDto> findClassesByTeacherId(Long teacherId) {
        String sql = """
                SELECT c.class_id AS classId,
                       c.user_id AS teacherId,
                       s.subject_id AS subjectId,
                       s.subject_name AS subjectName,
                       sec.section_id AS sectionId,
                       sec.section_name AS sectionName,
                       gl.grade_level_id AS gradeLevelId,
                       gl.grade_level_name AS gradeLevelName,
                       ay.academic_year_id AS academicYearId,
                       ay.year_name AS academicYear
                FROM `class` c
                JOIN subject s ON s.subject_id = c.subject_id
                JOIN section sec ON sec.section_id = c.section_id
                JOIN grade_level gl ON gl.grade_level_id = sec.grade_level_id
                JOIN academic_year ay ON ay.academic_year_id = c.academic_year_id
                WHERE c.user_id = ?
                ORDER BY c.class_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ClassSyncDto(
                rs.getLong("classId"),
                rs.getLong("teacherId"),
                rs.getLong("subjectId"),
                rs.getString("subjectName"),
                rs.getLong("sectionId"),
                rs.getString("sectionName"),
                rs.getLong("gradeLevelId"),
                rs.getString("gradeLevelName"),
                rs.getLong("academicYearId"),
                rs.getString("academicYear")
        ), teacherId);
    }

    public List<StudentSyncDto> findStudentsByTeacherId(Long teacherId) {
        String sql = """
                SELECT DISTINCT st.student_id AS studentId,
                                st.student_lrn AS studentLrn,
                                st.first_name AS firstName,
                                st.last_name AS lastName,
                                st.gender AS gender,
                                sec.section_id AS sectionId,
                                sec.section_name AS sectionName,
                                gl.grade_level_id AS gradeLevelId,
                                gl.grade_level_name AS gradeLevelName,
                                ay.academic_year_id AS academicYearId,
                                ay.year_name AS academicYear
                FROM student st
                JOIN student_enrollment se ON se.student_id = st.student_id
                JOIN section sec ON sec.section_id = se.section_id
                JOIN grade_level gl ON gl.grade_level_id = sec.grade_level_id
                JOIN academic_year ay ON ay.academic_year_id = se.academic_year_id
                JOIN `class` c
                  ON c.section_id = se.section_id
                 AND c.academic_year_id = se.academic_year_id
                WHERE c.user_id = ?
                ORDER BY st.student_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new StudentSyncDto(
                rs.getLong("studentId"),
                rs.getString("studentLrn"),
                rs.getString("firstName"),
                rs.getString("lastName"),
                rs.getString("gender"),
                rs.getLong("sectionId"),
                rs.getString("sectionName"),
                rs.getLong("gradeLevelId"),
                rs.getString("gradeLevelName"),
                rs.getLong("academicYearId"),
                rs.getString("academicYear")
        ), teacherId);
    }

    public List<TestSyncDto> findActiveTestsByTeacherId(Long teacherId) {
        String sql = """
                SELECT t.test_id,
                       t.class_id,
                       t.test_name,
                       t.test_type,
                       t.test_date,
                       t.test_status
                FROM `test` t
                JOIN `class` c ON c.class_id = t.class_id
                WHERE c.user_id = ?
                  AND t.test_status IN ('Active', 'Completed')
                ORDER BY t.test_date, t.test_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TestSyncDto(
                rs.getLong("test_id"),
                rs.getLong("class_id"),
                rs.getString("test_name"),
                rs.getString("test_type"),
                rs.getObject("test_date", Date.class).toLocalDate(),
                rs.getString("test_status")
        ), teacherId);
    }

    public List<TestPartSyncDto> findTestPartsByTeacherId(Long teacherId) {
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
                JOIN `test` t ON t.test_id = tp.test_id
                JOIN `class` c ON c.class_id = t.class_id
                JOIN competency_tags ct ON ct.competency_id = tp.competency_id
                WHERE c.user_id = ?
                ORDER BY tp.test_id, tp.test_part_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TestPartSyncDto(
                rs.getLong("test_part_id"),
                rs.getLong("test_id"),
                rs.getLong("competency_id"),
                rs.getString("competency_name"),
                rs.getString("part_order"),
                rs.getString("part_type"),
                rs.getInt("number_of_items"),
                rs.getInt("points_per_item"),
                rs.getString("answer_key")
        ), teacherId);
    }

    public List<CompetencySyncDto> findCompetenciesByTeacherId(Long teacherId) {
        String sql = """
                SELECT DISTINCT ct.competency_id,
                                ct.grade_level_id,
                                ct.subject_id,
                                ct.competency_name
                FROM competency_tags ct
                JOIN test_part tp ON tp.competency_id = ct.competency_id
                JOIN `test` t ON t.test_id = tp.test_id
                JOIN `class` c ON c.class_id = t.class_id
                WHERE c.user_id = ?
                ORDER BY ct.competency_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CompetencySyncDto(
                rs.getLong("competency_id"),
                rs.getLong("grade_level_id"),
                rs.getLong("subject_id"),
                rs.getString("competency_name")
        ), teacherId);
    }

    public List<TestResultSyncDto> findTestResultsByTeacherId(Long teacherId) {
        String sql = """
                SELECT tr.test_result_id AS testResultId,
                       tr.test_id AS testId,
                       tr.student_id AS studentId,
                       tr.total_score AS totalScore,
                       tr.raw_answers AS rawAnswers,
                       tr.checked_at AS checkedAt
                FROM test_result tr
                JOIN `test` t ON t.test_id = tr.test_id
                JOIN `class` c ON c.class_id = t.class_id
                WHERE c.user_id = ?
                ORDER BY tr.test_id, tr.student_id, tr.test_result_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TestResultSyncDto(
                rs.getLong("testResultId"),
                rs.getLong("testId"),
                rs.getLong("studentId"),
                rs.getInt("totalScore"),
                rs.getString("rawAnswers"),
                rs.getObject("checkedAt", Timestamp.class).toLocalDateTime()
        ), teacherId);
    }

    public List<ItemResponseSyncDto> findItemResponsesByTeacherId(Long teacherId) {
        String sql = """
                SELECT tir.item_result_id AS itemResultId,
                       tir.test_result_id AS testResultId,
                       tir.test_part_id AS testPartId,
                       tir.item_number AS itemNumber,
                       tir.is_correct AS isCorrect
                FROM test_item_result tir
                JOIN test_result tr ON tr.test_result_id = tir.test_result_id
                JOIN `test` t ON t.test_id = tr.test_id
                JOIN `class` c ON c.class_id = t.class_id
                WHERE c.user_id = ?
                ORDER BY tir.test_result_id, tir.test_part_id, tir.item_number, tir.item_result_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ItemResponseSyncDto(
                rs.getLong("itemResultId"),
                rs.getLong("testResultId"),
                rs.getLong("testPartId"),
                rs.getInt("itemNumber"),
                rs.getBoolean("isCorrect")
        ), teacherId);
    }

    public Optional<Long> findExistingTestResultId(Long testId, Long studentId) {
        String sql = """
                SELECT test_result_id
                FROM test_result
                WHERE test_id = ?
                  AND student_id = ?
                """;

        List<Long> resultIds = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("test_result_id"),
                testId,
                studentId
        );

        return resultIds.stream().findFirst();
    }

    public Long insertTestResult(Long testId, Long studentId, Integer totalScore, String rawAnswers) {
        String sql = """
                INSERT INTO test_result (test_id, student_id, total_score, raw_answers)
                VALUES (?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setLong(1, testId);
            preparedStatement.setLong(2, studentId);
            preparedStatement.setInt(3, totalScore);
            preparedStatement.setString(4, rawAnswers);
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated test_result_id").longValue();
    }

    public boolean itemResultExists(Long testResultId, Long testPartId, Integer itemNumber) {
        String sql = """
                SELECT COUNT(*)
                FROM test_item_result
                WHERE test_result_id = ?
                  AND test_part_id = ?
                  AND item_number = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, testResultId, testPartId, itemNumber);
        return count != null && count > 0;
    }

    public void insertItemResult(Long testPartId, Long testResultId, Integer itemNumber, Boolean isCorrect) {
        String sql = """
                INSERT INTO test_item_result (test_part_id, test_result_id, item_number, is_correct)
                VALUES (?, ?, ?, ?)
                """;

        jdbcTemplate.update(sql, testPartId, testResultId, itemNumber, isCorrect);
    }

    public void insertSyncLog(Long teacherId, Long testId, String syncStatus) {
        String sql = """
                INSERT INTO sync_log (user_id, test_id, sync_status)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(sql, teacherId, testId, syncStatus);
    }
}
