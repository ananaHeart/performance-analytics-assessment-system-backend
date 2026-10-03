package com.capstone.assessment.v3.report.repository;

import com.capstone.assessment.v3.school.model.V3AcademicCalendarLayout;

import com.capstone.assessment.v3.report.dto.V3LearningCompetencyReportResponse;
import com.capstone.assessment.v3.report.dto.V3ReportReferenceDataResponse;
import com.capstone.assessment.v3.report.dto.V3SyncActivityReportResponse;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentResultRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentScopeRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.CompetencyScopeRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.ConsolidatedGroupRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.ConsolidatedSkillAnswerTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.ConsolidatedSkillItemTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.ConsolidatedStatusCountRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.ItemAnalysisRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.SkillAnswerTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.SkillItemTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentAssessmentHistoryRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentClassContext;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentCompetencyTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentSkillAnswerTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentSkillItemTotalRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.StudentSkillMasteryRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.V3ReportGroupDimension;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3ReportRepository {

    private static final String TEACHER_NAME_SQL =
            "TRIM(CONCAT_WS(' ', teacher.first_name, NULLIF(teacher.middle_name, ''), "
                    + "teacher.last_name, suffix.suffix_name))";
    private static final String STUDENT_NAME_SQL =
            "TRIM(CONCAT_WS(' ', student.first_name, NULLIF(student.middle_name, ''), "
                    + "student.last_name, suffix.suffix_name))";

    private final JdbcTemplate jdbcTemplate;

    public V3ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<V3ReportReferenceDataResponse.SchoolOption> findSchool(String schoolId) {
        return jdbcTemplate.query(
                "SELECT school_id, school_name FROM school_profiles WHERE school_id = ?",
                (resultSet, rowNumber) -> new V3ReportReferenceDataResponse.SchoolOption(
                        resultSet.getString("school_id"),
                        resultSet.getString("school_name")
                ),
                schoolId
        ).stream().findFirst();
    }

    public Optional<String> findTeacherName(long userId) {
        return jdbcTemplate.query(
                "SELECT " + TEACHER_NAME_SQL + """
                         AS teacher_name
                          FROM users teacher
                          LEFT JOIN suffixes suffix ON suffix.suffix_id = teacher.suffix_id
                         WHERE teacher.user_id = ?
                        """,
                (resultSet, rowNumber) -> resultSet.getString("teacher_name"),
                userId
        ).stream().findFirst();
    }

    public List<V3ReportReferenceDataResponse.AcademicYearOption> listAcademicYears(
            String schoolId,
            Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" :
                """
                 AND EXISTS (
                       SELECT 1
                         FROM classes accessible_class
                         JOIN class_assignments accessible_assignment
                           ON accessible_assignment.class_id = accessible_class.class_id
                        WHERE accessible_class.academic_year_id = academic_year.academic_year_id
                          AND accessible_assignment.user_id = ?
                 )
                """;
        String sql = """
                SELECT academic_year.academic_year_id,
                       academic_year.year_name,
                       academic_year.start_date,
                       academic_year.end_date,
                       academic_year.status
                  FROM academic_years academic_year
                 WHERE academic_year.school_id = ?
                """ + teacherFilter + " ORDER BY academic_year.start_date DESC, academic_year.academic_year_id DESC";
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId}
                : new Object[]{schoolId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new V3ReportReferenceDataResponse.AcademicYearOption(
                        resultSet.getInt("academic_year_id"),
                        resultSet.getString("year_name"),
                        resultSet.getObject("start_date", LocalDate.class),
                        resultSet.getObject("end_date", LocalDate.class),
                        resultSet.getString("status")
                ), arguments);
    }

    public List<V3ReportReferenceDataResponse.TermPeriodOption> listTermPeriods(
            String schoolId,
            Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" :
                """
                 AND EXISTS (
                       SELECT 1
                         FROM classes accessible_class
                         JOIN class_assignments accessible_assignment
                           ON accessible_assignment.class_id = accessible_class.class_id
                        WHERE accessible_class.academic_year_id = academic_year.academic_year_id
                          AND accessible_assignment.user_id = ?
                 )
                """;
        String sql = """
                SELECT term.term_period_id,
                       term.academic_year_id,
                       term.term_name,
                       term.term_order,
                       term.start_at,
                       term.end_at,
                       term.status
                  FROM term_periods term
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = term.academic_year_id
                 WHERE academic_year.school_id = ?
                """ + teacherFilter + " ORDER BY academic_year.start_date DESC, term.term_order";
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId}
                : new Object[]{schoolId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new V3ReportReferenceDataResponse.TermPeriodOption(
                        resultSet.getInt("term_period_id"),
                        resultSet.getInt("academic_year_id"),
                        resultSet.getString("term_name"),
                        resultSet.getInt("term_order"),
                        requiredInstant(resultSet, "start_at"),
                        requiredInstant(resultSet, "end_at"),
                        resultSet.getString("status")
                ), arguments);
    }

    public List<V3ReportReferenceDataResponse.GradeLevelOption> listGradeLevels(
            String schoolId,
            Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" : " AND assignment.user_id = ?";
        String sql = """
                SELECT DISTINCT grade.grade_level_id, grade.grade_level_name
                  FROM grade_levels grade
                  JOIN sections section_row ON section_row.grade_level_id = grade.grade_level_id
                  JOIN classes class_row ON class_row.section_id = section_row.section_id
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = class_row.academic_year_id
                  JOIN class_assignments assignment ON assignment.class_id = class_row.class_id
                 WHERE section_row.school_id = ?
                   AND academic_year.school_id = ?
                """ + teacherFilter + " ORDER BY grade.grade_level_id";
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId, schoolId}
                : new Object[]{schoolId, schoolId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new V3ReportReferenceDataResponse.GradeLevelOption(
                        resultSet.getInt("grade_level_id"),
                        resultSet.getString("grade_level_name")
                ), arguments);
    }

    public List<V3ReportReferenceDataResponse.ClassOption> listClasses(
            String schoolId,
            Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" :
                """
                 AND EXISTS (
                       SELECT 1
                         FROM class_assignments accessible_assignment
                        WHERE accessible_assignment.class_id = class_row.class_id
                          AND accessible_assignment.user_id = ?
                 )
                """;
        String sql = """
                SELECT class_row.class_id,
                       academic_year.academic_year_id,
                       academic_year.year_name AS academic_year_name,
                       grade.grade_level_id,
                       grade.grade_level_name,
                       section_row.section_id,
                       section_row.section_name,
                       class_row.status
                  FROM classes class_row
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = class_row.academic_year_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                  JOIN grade_levels grade ON grade.grade_level_id = section_row.grade_level_id
                 WHERE section_row.school_id = ?
                   AND academic_year.school_id = ?
                   AND class_row.status <> 'archived'
                """ + teacherFilter
                + " ORDER BY academic_year.start_date DESC, grade.grade_level_id, section_row.section_name";
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId, schoolId}
                : new Object[]{schoolId, schoolId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new V3ReportReferenceDataResponse.ClassOption(
                        resultSet.getLong("class_id"),
                        resultSet.getInt("academic_year_id"),
                        resultSet.getString("academic_year_name"),
                        resultSet.getInt("grade_level_id"),
                        resultSet.getString("grade_level_name"),
                        resultSet.getInt("section_id"),
                        resultSet.getString("section_name"),
                        resultSet.getString("status")
                ), arguments);
    }

    public List<V3ReportReferenceDataResponse.TeacherOption> listTeachers(
            String schoolId,
            Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" : " AND teacher.user_id = ?";
        String sql = """
                SELECT teacher.user_id,
                       %s AS full_name,
                       status_ref.status_name
                  FROM users teacher
                  JOIN roles role_ref ON role_ref.role_id = teacher.role_id
                  JOIN statuses status_ref ON status_ref.status_id = teacher.status_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = teacher.suffix_id
                 WHERE teacher.school_id = ?
                   AND role_ref.role_name = 'teacher'
                """.formatted(TEACHER_NAME_SQL)
                + teacherFilter
                + " ORDER BY teacher.last_name, teacher.first_name, teacher.user_id";
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId}
                : new Object[]{schoolId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new V3ReportReferenceDataResponse.TeacherOption(
                        resultSet.getLong("user_id"),
                        resultSet.getString("full_name"),
                        resultSet.getString("status_name")
                ), arguments);
    }

    public List<V3ReportReferenceDataResponse.SubjectOption> listSubjects(
            String schoolId,
            Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" : " AND assignment.user_id = ?";
        String sql = """
                SELECT DISTINCT subject.subject_id, subject.subject_code, subject.subject_name
                  FROM subjects subject
                  JOIN class_assignments assignment ON assignment.subject_id = subject.subject_id
                  JOIN classes class_row ON class_row.class_id = assignment.class_id
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = class_row.academic_year_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                 WHERE section_row.school_id = ?
                   AND academic_year.school_id = ?
                """ + teacherFilter + " ORDER BY subject.subject_name";
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId, schoolId}
                : new Object[]{schoolId, schoolId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new V3ReportReferenceDataResponse.SubjectOption(
                        resultSet.getInt("subject_id"),
                        resultSet.getString("subject_code"),
                        resultSet.getString("subject_name")
                ), arguments);
    }

    public List<V3ReportReferenceDataResponse.ClassAssignmentOption> listClassAssignments(
            String schoolId,
            Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" : " AND assignment.user_id = ?";
        String sql = """
                SELECT assignment.class_assignment_id,
                       assignment.class_id,
                       assignment.user_id AS teacher_user_id,
                       assignment.subject_id,
                       academic_year.academic_year_id,
                       grade.grade_level_id,
                       section_row.section_id,
                       %s AS teacher_name,
                       subject.subject_name,
                       academic_year.year_name AS academic_year_name,
                       grade.grade_level_name,
                       section_row.section_name,
                       assignment.status
                  FROM class_assignments assignment
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = teacher.suffix_id
                  JOIN subjects subject ON subject.subject_id = assignment.subject_id
                  JOIN classes class_row ON class_row.class_id = assignment.class_id
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = class_row.academic_year_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                  JOIN grade_levels grade ON grade.grade_level_id = section_row.grade_level_id
                 WHERE teacher.school_id = ?
                   AND section_row.school_id = ?
                   AND academic_year.school_id = ?
                   AND class_row.status <> 'archived'
                """.formatted(TEACHER_NAME_SQL)
                + teacherFilter
                + " ORDER BY academic_year.start_date DESC, grade.grade_level_id, section_row.section_name, subject.subject_name";
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId, schoolId, schoolId}
                : new Object[]{schoolId, schoolId, schoolId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new V3ReportReferenceDataResponse.ClassAssignmentOption(
                        resultSet.getLong("class_assignment_id"),
                        resultSet.getLong("class_id"),
                        resultSet.getLong("teacher_user_id"),
                        resultSet.getInt("subject_id"),
                        resultSet.getInt("academic_year_id"),
                        resultSet.getInt("grade_level_id"),
                        resultSet.getInt("section_id"),
                        resultSet.getString("teacher_name"),
                        resultSet.getString("subject_name"),
                        resultSet.getString("academic_year_name"),
                        resultSet.getString("grade_level_name"),
                        resultSet.getString("section_name"),
                        resultSet.getString("status")
                ), arguments);
    }

    public List<V3ReportReferenceDataResponse.AssessmentOption> listAssessments(
            String schoolId,
            Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" : " AND assignment.user_id = ?";
        String sql = """
                SELECT test.test_id,
                       test_assignment.test_assignment_id,
                       assignment.class_assignment_id,
                       test.term_period_id,
                       test.test_name,
                       test.test_type,
                       test.status,
                       test_assignment.assignment_status,
                       test_assignment.open_at,
                       test_assignment.close_at
                  FROM tests test
                  JOIN test_assignments test_assignment ON test_assignment.test_id = test.test_id
                  JOIN class_assignments assignment
                    ON assignment.class_assignment_id = test_assignment.class_assignment_id
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  JOIN classes class_row ON class_row.class_id = assignment.class_id
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = class_row.academic_year_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                 WHERE test.school_id = ?
                   AND teacher.school_id = ?
                   AND section_row.school_id = ?
                   AND academic_year.school_id = ?
                """ + teacherFilter + " ORDER BY test.updated_at DESC, test.test_id DESC";
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId, schoolId, schoolId, schoolId}
                : new Object[]{schoolId, schoolId, schoolId, schoolId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new V3ReportReferenceDataResponse.AssessmentOption(
                        resultSet.getLong("test_id"),
                        resultSet.getLong("test_assignment_id"),
                        resultSet.getLong("class_assignment_id"),
                        resultSet.getInt("term_period_id"),
                        resultSet.getString("test_name"),
                        resultSet.getString("test_type"),
                        resultSet.getString("status"),
                        resultSet.getString("assignment_status"),
                        nullableInstant(resultSet, "open_at"),
                        nullableInstant(resultSet, "close_at")
                ), arguments);
    }

    public List<V3ReportReferenceDataResponse.StudentOption> listStudents(
            String schoolId,
            Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" :
                """
                 AND EXISTS (
                       SELECT 1
                         FROM class_assignments accessible_assignment
                        WHERE accessible_assignment.class_id = class_list.class_id
                          AND accessible_assignment.user_id = ?
                 )
                """;
        String sql = """
                SELECT student.student_id,
                       class_list.class_list_id,
                       class_list.class_id,
                       student.student_lrn,
                       %s AS full_name,
                       class_list.enrollment_status
                  FROM class_lists class_list
                  JOIN students student ON student.student_id = class_list.student_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = student.suffix_id
                  JOIN classes class_row ON class_row.class_id = class_list.class_id
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = class_row.academic_year_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                 WHERE student.school_id = ?
                   AND section_row.school_id = ?
                   AND academic_year.school_id = ?
                """.formatted(STUDENT_NAME_SQL)
                + teacherFilter
                + " ORDER BY student.last_name, student.first_name, student.student_id, class_list.class_list_id";
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId, schoolId, schoolId}
                : new Object[]{schoolId, schoolId, schoolId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new V3ReportReferenceDataResponse.StudentOption(
                        resultSet.getLong("student_id"),
                        resultSet.getLong("class_list_id"),
                        resultSet.getLong("class_id"),
                        resultSet.getString("student_lrn"),
                        resultSet.getString("full_name"),
                        resultSet.getString("enrollment_status")
                ), arguments);
    }

    public Optional<String> findTestSchoolId(long testId) {
        return jdbcTemplate.query(
                "SELECT school_id FROM tests WHERE test_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("school_id"),
                testId
        ).stream().findFirst();
    }

    public Optional<AssessmentScopeRow> findAssessmentScope(long testId, long classAssignmentId) {
        String sql = """
                SELECT school.school_id,
                       school.school_name,
                       academic_year.academic_year_id,
                       academic_year.year_name AS academic_year_name,
                       term.term_period_id,
                       term.term_name,
                       class_row.class_id,
                       grade.grade_level_id,
                       grade.grade_level_name,
                       section_row.section_id,
                       section_row.section_name,
                       assignment.class_assignment_id,
                       teacher.user_id AS teacher_user_id,
                       %s AS teacher_name,
                       subject.subject_id,
                       subject.subject_name,
                       test.test_id,
                       test_assignment.test_assignment_id,
                       test.test_name,
                       test.test_type,
                       test.status AS test_status,
                       test_assignment.assignment_status,
                       test_assignment.open_at,
                       test_assignment.close_at,
                       COALESCE((
                           SELECT SUM(question.maximum_points)
                             FROM test_parts part
                             JOIN questions question ON question.test_part_id = part.test_part_id
                            WHERE part.test_id = test.test_id
                       ), 0.00) AS configured_maximum_points
                  FROM tests test
                  JOIN school_profiles school ON school.school_id = test.school_id
                  JOIN term_periods term ON term.term_period_id = test.term_period_id
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = term.academic_year_id
                  JOIN test_assignments test_assignment ON test_assignment.test_id = test.test_id
                  JOIN class_assignments assignment
                    ON assignment.class_assignment_id = test_assignment.class_assignment_id
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = teacher.suffix_id
                  JOIN subjects subject ON subject.subject_id = assignment.subject_id
                  JOIN classes class_row ON class_row.class_id = assignment.class_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                  JOIN grade_levels grade ON grade.grade_level_id = section_row.grade_level_id
                 WHERE test.test_id = ?
                   AND assignment.class_assignment_id = ?
                """.formatted(TEACHER_NAME_SQL);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new AssessmentScopeRow(
                        resultSet.getString("school_id"),
                        resultSet.getString("school_name"),
                        resultSet.getInt("academic_year_id"),
                        resultSet.getString("academic_year_name"),
                        resultSet.getInt("term_period_id"),
                        resultSet.getString("term_name"),
                        resultSet.getLong("class_id"),
                        resultSet.getInt("grade_level_id"),
                        resultSet.getString("grade_level_name"),
                        resultSet.getInt("section_id"),
                        resultSet.getString("section_name"),
                        resultSet.getLong("class_assignment_id"),
                        resultSet.getLong("teacher_user_id"),
                        resultSet.getString("teacher_name"),
                        resultSet.getInt("subject_id"),
                        resultSet.getString("subject_name"),
                        resultSet.getLong("test_id"),
                        resultSet.getLong("test_assignment_id"),
                        resultSet.getString("test_name"),
                        resultSet.getString("test_type"),
                        resultSet.getString("test_status"),
                        resultSet.getString("assignment_status"),
                        nullableInstant(resultSet, "open_at"),
                        nullableInstant(resultSet, "close_at"),
                        resultSet.getBigDecimal("configured_maximum_points")
                ),
                testId,
                classAssignmentId
        ).stream().findFirst();
    }

    public List<AssessmentResultRow> listLatestAssessmentResults(AssessmentScopeRow scope) {
        String sql = """
                SELECT student.student_id,
                       class_list.class_list_id,
                       student.student_lrn,
                       %s AS full_name,
                       class_list.enrollment_status,
                       result.test_result_id,
                       result.result_uuid,
                       COALESCE(result.result_status, 'not_submitted') AS result_status,
                       result.submitted_at,
                       result.verification_completed_at,
                       CASE WHEN result.result_status = 'finalized' THEN result.total_score END AS earned_points,
                       CASE WHEN result.result_status = 'finalized' THEN result.max_score END AS maximum_points,
                       CASE WHEN result.result_status = 'finalized' THEN result.percentage_snapshot END AS percentage,
                       CASE WHEN result.result_status = 'finalized' THEN result.performance_status END AS performance_status,
                       CASE WHEN result.result_status = 'finalized' THEN rule_set.performance_rule_set_id END
                           AS performance_rule_set_id,
                       CASE WHEN result.result_status = 'finalized' THEN rule_set.rule_set_name END AS rule_set_name,
                       CASE WHEN result.result_status = 'finalized' THEN rule_set.rule_version END AS rule_version,
                       CASE WHEN result.result_status = 'finalized' THEN rule_set.rule_definition END AS rule_definition,
                       CASE WHEN result.test_result_id IS NULL THEN 0 ELSE (
                           SELECT COUNT(*)
                             FROM student_answers answer_row
                            WHERE answer_row.test_result_id = result.test_result_id
                              AND (
                                  answer_row.evaluation_status <> 'finalized'
                                  OR answer_row.verified_by_user_id IS NULL
                                  OR answer_row.verified_at IS NULL
                                  OR answer_row.finalized_at IS NULL
                              )
                       ) END AS pending_verification_count
                  FROM class_lists class_list
                  JOIN students student ON student.student_id = class_list.student_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = student.suffix_id
                  LEFT JOIN test_results result
                    ON result.test_assignment_id = ?
                   AND result.class_list_id = class_list.class_list_id
                   AND result.result_status <> 'superseded'
                   AND NOT EXISTS (
                       SELECT 1
                         FROM test_results newer_result
                        WHERE newer_result.test_assignment_id = result.test_assignment_id
                          AND newer_result.class_list_id = result.class_list_id
                          AND newer_result.result_status <> 'superseded'
                          AND (
                              newer_result.attempt_number > result.attempt_number
                              OR (
                                  newer_result.attempt_number = result.attempt_number
                                  AND newer_result.test_result_id > result.test_result_id
                              )
                          )
                   )
                  LEFT JOIN performance_rule_sets rule_set
                    ON rule_set.performance_rule_set_id = result.performance_rule_set_id
                 WHERE class_list.class_id = ?
                   AND student.school_id = ?
                  ORDER BY student.last_name, student.first_name, student.student_id
                """.formatted(STUDENT_NAME_SQL);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new AssessmentResultRow(
                        resultSet.getLong("student_id"),
                        resultSet.getLong("class_list_id"),
                        resultSet.getString("student_lrn"),
                        resultSet.getString("full_name"),
                        resultSet.getString("enrollment_status"),
                        nullableLong(resultSet, "test_result_id"),
                        resultSet.getString("result_uuid"),
                        resultSet.getString("result_status"),
                        nullableInstant(resultSet, "submitted_at"),
                        nullableInstant(resultSet, "verification_completed_at"),
                        resultSet.getBigDecimal("earned_points"),
                        resultSet.getBigDecimal("maximum_points"),
                        resultSet.getBigDecimal("percentage"),
                        resultSet.getString("performance_status"),
                        nullableLong(resultSet, "performance_rule_set_id"),
                        resultSet.getString("rule_set_name"),
                        resultSet.getString("rule_version"),
                        resultSet.getString("rule_definition"),
                        resultSet.getInt("pending_verification_count")
                ),
                scope.testAssignmentId(),
                scope.classId(),
                scope.schoolId()
        );
    }

    public List<ItemAnalysisRow> listItemAnalysis(AssessmentScopeRow scope) {
        String sql = """
                SELECT question.question_id,
                       question.test_part_id,
                       question.item_number,
                       type.question_type_code,
                       (SELECT COUNT(*) FROM class_lists roster
                         WHERE roster.class_id = ? AND roster.enrollment_status = 'enrolled') AS roster_count,
                       COUNT(DISTINCT CASE WHEN answer_row.is_correct = 1
                                           THEN answer_row.test_result_id END) AS correct_count,
                       COUNT(DISTINCT CASE WHEN answer_row.is_correct = 0
                                           THEN answer_row.test_result_id END) AS incorrect_count,
                       GROUP_CONCAT(DISTINCT mapping.skill_id) AS skill_ids
                  FROM test_parts part
                  JOIN questions question ON question.test_part_id = part.test_part_id
                  JOIN question_types type ON type.question_type_id = question.question_type_id
                  LEFT JOIN part_skill_mappings mapping
                    ON mapping.test_part_id = question.test_part_id
                   AND question.item_number BETWEEN mapping.start_item_number AND mapping.end_item_number
                  LEFT JOIN student_answers answer_row
                    ON answer_row.question_id = question.question_id
                   AND answer_row.test_result_id IN (
                       SELECT result.test_result_id
                         FROM test_results result
                        WHERE result.test_assignment_id = ? AND result.result_status = 'finalized'
                   )
                 WHERE part.test_id = ?
                 GROUP BY question.question_id, question.test_part_id, question.item_number,
                          type.question_type_code
                 ORDER BY part.part_order, question.item_number
                """;
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> {
                    int correct = resultSet.getInt("correct_count");
                    int incorrect = resultSet.getInt("incorrect_count");
                    int roster = resultSet.getInt("roster_count");
                    return new ItemAnalysisRow(
                            resultSet.getLong("question_id"),
                            resultSet.getLong("test_part_id"),
                            resultSet.getInt("item_number"),
                            resultSet.getString("question_type_code"),
                            correct,
                            incorrect,
                            Math.max(0, roster - correct - incorrect),
                            parseSkillIds(resultSet.getString("skill_ids"))
                    );
                },
                scope.classId(),
                scope.testAssignmentId(),
                scope.testId()
        );
    }

    /** A skill can be referenced by more than one part_skill_mappings row (e.g. one range per
     *  question instead of one range per part) - both queries below dedupe on the underlying
     *  (skill, question) or (skill, question, test_result) pair via a DISTINCT derived table
     *  before aggregating, so a skill with several mapping rows is never double-counted. */
    public List<SkillItemTotalRow> listSkillItemTotals(long testId) {
        String sql = """
                SELECT skill_id, competency_name,
                       COUNT(*) AS assessed_item_count,
                       COALESCE(SUM(maximum_points), 0) AS possible_points_per_student
                  FROM (
                      SELECT DISTINCT skill.skill_id, competency.competency_name,
                             question.question_id, question.maximum_points
                        FROM part_skill_mappings mapping
                        JOIN skills skill ON skill.skill_id = mapping.skill_id
                        JOIN competency_tags competency ON competency.competency_id = skill.competency_id
                        JOIN test_parts part ON part.test_part_id = mapping.test_part_id
                        JOIN questions question
                          ON question.test_part_id = mapping.test_part_id
                         AND question.item_number BETWEEN mapping.start_item_number AND mapping.end_item_number
                       WHERE part.test_id = ?
                  ) distinct_skill_questions
                 GROUP BY skill_id, competency_name
                 ORDER BY competency_name
                """;
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new SkillItemTotalRow(
                        resultSet.getLong("skill_id"),
                        resultSet.getString("competency_name"),
                        resultSet.getInt("assessed_item_count"),
                        resultSet.getBigDecimal("possible_points_per_student")
                ),
                testId
        );
    }

    public List<SkillAnswerTotalRow> listSkillAnswerTotals(long testAssignmentId, long testId) {
        String sql = """
                SELECT skill_id,
                       COUNT(DISTINCT test_result_id) AS student_count,
                       COALESCE(SUM(points_earned), 0) AS earned_points
                  FROM (
                      SELECT DISTINCT skill.skill_id, answer_row.test_result_id, answer_row.question_id,
                             answer_row.points_earned
                        FROM part_skill_mappings mapping
                        JOIN skills skill ON skill.skill_id = mapping.skill_id
                        JOIN test_parts part ON part.test_part_id = mapping.test_part_id
                        JOIN questions question
                          ON question.test_part_id = mapping.test_part_id
                         AND question.item_number BETWEEN mapping.start_item_number AND mapping.end_item_number
                        JOIN student_answers answer_row ON answer_row.question_id = question.question_id
                       WHERE part.test_id = ?
                         AND answer_row.test_result_id IN (
                             SELECT result.test_result_id
                               FROM test_results result
                              WHERE result.test_assignment_id = ? AND result.result_status = 'finalized'
                         )
                  ) distinct_skill_answers
                 GROUP BY skill_id
                """;
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new SkillAnswerTotalRow(
                        resultSet.getLong("skill_id"),
                        resultSet.getInt("student_count"),
                        resultSet.getBigDecimal("earned_points")
                ),
                testId,
                testAssignmentId
        );
    }

    /** Per-result breakdown of {@link #listSkillAnswerTotals}: same DISTINCT subquery and the
     *  same finalized-only filter, grouped per result instead of summed per skill, so each
     *  student's numbers add up exactly to the class-level total. Possible points per student
     *  come from {@link #listSkillItemTotals} (identical for every student on a test). */
    public List<StudentSkillMasteryRow> listStudentSkillMastery(long testAssignmentId, long testId) {
        String sql = ("""
                SELECT distinct_answers.skill_id,
                       distinct_answers.test_result_id,
                       student.student_id,
                       %s AS full_name,
                       COALESCE(SUM(distinct_answers.points_earned), 0) AS earned_points
                  FROM (
                      SELECT DISTINCT mapping.skill_id, answer_row.test_result_id, answer_row.question_id,
                             answer_row.points_earned
                        FROM part_skill_mappings mapping
                        JOIN test_parts part ON part.test_part_id = mapping.test_part_id
                        JOIN questions question
                          ON question.test_part_id = mapping.test_part_id
                         AND question.item_number BETWEEN mapping.start_item_number AND mapping.end_item_number
                        JOIN student_answers answer_row ON answer_row.question_id = question.question_id
                       WHERE part.test_id = ?
                         AND answer_row.test_result_id IN (
                             SELECT result.test_result_id
                               FROM test_results result
                              WHERE result.test_assignment_id = ? AND result.result_status = 'finalized'
                         )
                  ) distinct_answers
                  JOIN test_results result ON result.test_result_id = distinct_answers.test_result_id
                  JOIN class_lists class_list ON class_list.class_list_id = result.class_list_id
                  JOIN students student ON student.student_id = class_list.student_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = student.suffix_id
                 GROUP BY distinct_answers.skill_id, distinct_answers.test_result_id, student.student_id,
                          student.first_name, student.middle_name, student.last_name, suffix.suffix_name
                """).formatted(STUDENT_NAME_SQL);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new StudentSkillMasteryRow(
                        resultSet.getLong("skill_id"),
                        resultSet.getLong("test_result_id"),
                        resultSet.getLong("student_id"),
                        resultSet.getString("full_name"),
                        resultSet.getBigDecimal("earned_points")
                ),
                testId,
                testAssignmentId
        );
    }

    // ---------------------------------------------------------------------------------
    // Principal Consolidated Report
    // ---------------------------------------------------------------------------------

    /** Trusted, hardcoded column expressions - never build these from caller input. */
    private static final Map<V3ReportGroupDimension, String> CONSOLIDATED_GROUP_COLUMNS = Map.of(
            V3ReportGroupDimension.ACADEMIC_YEAR, "academic_year.academic_year_id",
            V3ReportGroupDimension.TERM_PERIOD, "term.term_period_id",
            V3ReportGroupDimension.GRADE_LEVEL, "grade.grade_level_id",
            V3ReportGroupDimension.CLASS, "class_row.class_id",
            V3ReportGroupDimension.TEACHER, "teacher.user_id",
            V3ReportGroupDimension.SUBJECT, "subject.subject_id"
    );

    private static final Map<V3ReportGroupDimension, String> CONSOLIDATED_GROUP_LABELS = Map.of(
            V3ReportGroupDimension.ACADEMIC_YEAR, "academic_year.year_name",
            V3ReportGroupDimension.TERM_PERIOD, "term.term_name",
            V3ReportGroupDimension.GRADE_LEVEL, "grade.grade_level_name",
            V3ReportGroupDimension.CLASS, "CONCAT(grade.grade_level_name, ' - ', section_row.section_name)",
            V3ReportGroupDimension.TEACHER, TEACHER_NAME_SQL,
            V3ReportGroupDimension.SUBJECT, "subject.subject_name"
    );

    private record ConsolidatedFilter(String sql, List<Object> params) {
    }

    /** Every consolidated query shares this roster-to-test join chain and optional-filter set.
     *  Filters are always fixed "AND column = ?" fragments with bound parameters, mirroring the
     *  teacherFilter idiom already used throughout this class - never string-interpolated values. */
    private ConsolidatedFilter buildConsolidatedFilter(
            String schoolId, Long teacherUserId, Integer academicYearId, Integer termPeriodId,
            Integer gradeLevelId, Long classId, Long filterTeacherUserId, Integer subjectId, Long testId
    ) {
        StringBuilder sql = new StringBuilder(" WHERE test.school_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(schoolId);
        Long effectiveTeacherId = teacherUserId != null ? teacherUserId : filterTeacherUserId;
        if (effectiveTeacherId != null) {
            sql.append(" AND assignment.user_id = ?");
            params.add(effectiveTeacherId);
        }
        if (academicYearId != null) {
            sql.append(" AND academic_year.academic_year_id = ?");
            params.add(academicYearId);
        }
        if (termPeriodId != null) {
            sql.append(" AND term.term_period_id = ?");
            params.add(termPeriodId);
        }
        if (gradeLevelId != null) {
            sql.append(" AND grade.grade_level_id = ?");
            params.add(gradeLevelId);
        }
        if (classId != null) {
            sql.append(" AND class_row.class_id = ?");
            params.add(classId);
        }
        if (subjectId != null) {
            sql.append(" AND subject.subject_id = ?");
            params.add(subjectId);
        }
        if (testId != null) {
            sql.append(" AND test.test_id = ?");
            params.add(testId);
        }
        return new ConsolidatedFilter(sql.toString(), params);
    }

    private static final String CONSOLIDATED_BASE_JOIN = """
              FROM class_lists class_list
              JOIN classes class_row ON class_row.class_id = class_list.class_id
              JOIN academic_years academic_year ON academic_year.academic_year_id = class_row.academic_year_id
              JOIN sections section_row ON section_row.section_id = class_row.section_id
              JOIN grade_levels grade ON grade.grade_level_id = section_row.grade_level_id
              JOIN class_assignments assignment ON assignment.class_id = class_row.class_id
              JOIN users teacher ON teacher.user_id = assignment.user_id
              LEFT JOIN suffixes suffix ON suffix.suffix_id = teacher.suffix_id
              JOIN subjects subject ON subject.subject_id = assignment.subject_id
              JOIN test_assignments test_assignment ON test_assignment.class_assignment_id = assignment.class_assignment_id
              JOIN tests test ON test.test_id = test_assignment.test_id
              JOIN term_periods term ON term.term_period_id = test.term_period_id
            """;

    public List<ConsolidatedGroupRow> listConsolidatedGroups(
            V3ReportGroupDimension dimension, String schoolId, Long teacherUserId,
            Integer academicYearId, Integer termPeriodId, Integer gradeLevelId,
            Long classId, Long filterTeacherUserId, Integer subjectId, Long testId
    ) {
        String groupColumn = CONSOLIDATED_GROUP_COLUMNS.get(dimension);
        String groupLabel = CONSOLIDATED_GROUP_LABELS.get(dimension);
        ConsolidatedFilter filter = buildConsolidatedFilter(schoolId, teacherUserId, academicYearId,
                termPeriodId, gradeLevelId, classId, filterTeacherUserId, subjectId, testId);
        String sql = ("SELECT CAST(%s AS CHAR) AS group_key, %s AS group_label, "
                + "COUNT(DISTINCT class_list.class_list_id) AS student_count, "
                + "COALESCE(SUM(result.total_score), 0) AS earned_points, "
                + "COALESCE(SUM(result.max_score), 0) AS possible_points "
                + CONSOLIDATED_BASE_JOIN
                + "  LEFT JOIN test_results result "
                + "    ON result.test_assignment_id = test_assignment.test_assignment_id "
                + "   AND result.class_list_id = class_list.class_list_id "
                + "   AND result.result_status = 'finalized' "
                + filter.sql()
                + " GROUP BY %s, %s ORDER BY %s")
                .formatted(groupColumn, groupLabel, groupColumn, groupLabel, groupLabel);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new ConsolidatedGroupRow(
                        resultSet.getString("group_key"),
                        resultSet.getString("group_label"),
                        resultSet.getInt("student_count"),
                        resultSet.getBigDecimal("earned_points"),
                        resultSet.getBigDecimal("possible_points")
                ),
                filter.params().toArray()
        );
    }

    public List<ConsolidatedStatusCountRow> listConsolidatedStatusCounts(
            V3ReportGroupDimension dimension, String schoolId, Long teacherUserId,
            Integer academicYearId, Integer termPeriodId, Integer gradeLevelId,
            Long classId, Long filterTeacherUserId, Integer subjectId, Long testId
    ) {
        String groupColumn = CONSOLIDATED_GROUP_COLUMNS.get(dimension);
        ConsolidatedFilter filter = buildConsolidatedFilter(schoolId, teacherUserId, academicYearId,
                termPeriodId, gradeLevelId, classId, filterTeacherUserId, subjectId, testId);
        String sql = ("SELECT CAST(%s AS CHAR) AS group_key, result.performance_status, COUNT(*) AS status_count "
                + CONSOLIDATED_BASE_JOIN
                + "  JOIN test_results result "
                + "    ON result.test_assignment_id = test_assignment.test_assignment_id "
                + "   AND result.class_list_id = class_list.class_list_id "
                + "   AND result.result_status = 'finalized' "
                + filter.sql()
                + "   AND result.performance_status IS NOT NULL"
                + " GROUP BY %s, result.performance_status")
                .formatted(groupColumn, groupColumn);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new ConsolidatedStatusCountRow(
                        resultSet.getString("group_key"),
                        resultSet.getString("performance_status"),
                        resultSet.getInt("status_count")
                ),
                filter.params().toArray()
        );
    }

    public List<ConsolidatedSkillItemTotalRow> listConsolidatedSkillItemTotals(
            V3ReportGroupDimension dimension, String schoolId, Long teacherUserId,
            Integer academicYearId, Integer termPeriodId, Integer gradeLevelId,
            Long classId, Long filterTeacherUserId, Integer subjectId, Long testId
    ) {
        String groupColumn = CONSOLIDATED_GROUP_COLUMNS.get(dimension);
        ConsolidatedFilter filter = buildConsolidatedFilter(schoolId, teacherUserId, academicYearId,
                termPeriodId, gradeLevelId, classId, filterTeacherUserId, subjectId, testId);
        String sql = ("""
                SELECT group_key, skill_id, competency_name,
                       COUNT(*) AS assessed_item_count,
                       COALESCE(SUM(maximum_points), 0) AS possible_points_per_student
                  FROM (
                      SELECT DISTINCT CAST(%s AS CHAR) AS group_key, skill.skill_id,
                             competency.competency_name, question.question_id, question.maximum_points
                """
                + CONSOLIDATED_BASE_JOIN
                + """
                      JOIN part_skill_mappings mapping ON mapping.test_part_id IN (
                          SELECT test_part_id FROM test_parts WHERE test_id = test.test_id
                      )
                      JOIN skills skill ON skill.skill_id = mapping.skill_id
                      JOIN competency_tags competency ON competency.competency_id = skill.competency_id
                      JOIN questions question
                        ON question.test_part_id = mapping.test_part_id
                       AND question.item_number BETWEEN mapping.start_item_number AND mapping.end_item_number
                """
                + filter.sql()
                + """
                  ) distinct_group_skill_questions
                 GROUP BY group_key, skill_id, competency_name
                """)
                .formatted(groupColumn);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new ConsolidatedSkillItemTotalRow(
                        resultSet.getString("group_key"),
                        resultSet.getLong("skill_id"),
                        resultSet.getString("competency_name"),
                        resultSet.getInt("assessed_item_count"),
                        resultSet.getBigDecimal("possible_points_per_student")
                ),
                filter.params().toArray()
        );
    }

    public List<ConsolidatedSkillAnswerTotalRow> listConsolidatedSkillAnswerTotals(
            V3ReportGroupDimension dimension, String schoolId, Long teacherUserId,
            Integer academicYearId, Integer termPeriodId, Integer gradeLevelId,
            Long classId, Long filterTeacherUserId, Integer subjectId, Long testId
    ) {
        String groupColumn = CONSOLIDATED_GROUP_COLUMNS.get(dimension);
        ConsolidatedFilter filter = buildConsolidatedFilter(schoolId, teacherUserId, academicYearId,
                termPeriodId, gradeLevelId, classId, filterTeacherUserId, subjectId, testId);
        String sql = ("""
                SELECT group_key, skill_id,
                       COUNT(DISTINCT test_result_id) AS student_count,
                       COALESCE(SUM(points_earned), 0) AS earned_points
                  FROM (
                      SELECT DISTINCT CAST(%s AS CHAR) AS group_key, skill.skill_id,
                             answer_row.test_result_id, answer_row.question_id, answer_row.points_earned
                """
                + CONSOLIDATED_BASE_JOIN
                + """
                      JOIN test_results result
                        ON result.test_assignment_id = test_assignment.test_assignment_id
                       AND result.class_list_id = class_list.class_list_id
                       AND result.result_status = 'finalized'
                      JOIN part_skill_mappings mapping ON mapping.test_part_id IN (
                          SELECT test_part_id FROM test_parts WHERE test_id = test.test_id
                      )
                      JOIN skills skill ON skill.skill_id = mapping.skill_id
                      JOIN questions question
                        ON question.test_part_id = mapping.test_part_id
                       AND question.item_number BETWEEN mapping.start_item_number AND mapping.end_item_number
                      JOIN student_answers answer_row ON answer_row.question_id = question.question_id
                                                       AND answer_row.test_result_id = result.test_result_id
                """
                + filter.sql()
                + """
                  ) distinct_group_skill_answers
                 GROUP BY group_key, skill_id
                """)
                .formatted(groupColumn);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new ConsolidatedSkillAnswerTotalRow(
                        resultSet.getString("group_key"),
                        resultSet.getLong("skill_id"),
                        resultSet.getInt("student_count"),
                        resultSet.getBigDecimal("earned_points")
                ),
                filter.params().toArray()
        );
    }

    // ---------------------------------------------------------------------------------
    // Individual Student Performance Profile
    // ---------------------------------------------------------------------------------

    public record StudentIdentity(String studentLrn, String fullName) {
    }

    public Optional<StudentIdentity> findStudentIdentity(long studentId, String schoolId) {
        String sql = ("""
                SELECT student.student_lrn, %s AS full_name
                  FROM students student
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = student.suffix_id
                 WHERE student.student_id = ? AND student.school_id = ?
                """).formatted(STUDENT_NAME_SQL);
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new StudentIdentity(
                resultSet.getString("student_lrn"),
                resultSet.getString("full_name")
        ), studentId, schoolId).stream().findFirst();
    }

    /** A student can have more than one class_list_id (re-enrollment, class change). This
     *  resolves every one within the caller's access scope; callers must pass the FULL list to
     *  every downstream query below - never a single class_list_id for this report. */
    public List<Long> listClassListIdsForStudent(long studentId, String schoolId, Long teacherUserId) {
        String teacherFilter = teacherUserId == null ? "" :
                """
                 AND EXISTS (
                       SELECT 1
                         FROM class_assignments accessible_assignment
                        WHERE accessible_assignment.class_id = class_list.class_id
                          AND accessible_assignment.user_id = ?
                 )
                """;
        String sql = """
                SELECT class_list.class_list_id
                  FROM class_lists class_list
                  JOIN students student ON student.student_id = class_list.student_id
                  JOIN classes class_row ON class_row.class_id = class_list.class_id
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = class_row.academic_year_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                 WHERE class_list.student_id = ?
                   AND student.school_id = ?
                   AND section_row.school_id = ?
                   AND academic_year.school_id = ?
                """ + teacherFilter;
        Object[] arguments = teacherUserId == null
                ? new Object[]{studentId, schoolId, schoolId, schoolId}
                : new Object[]{studentId, schoolId, schoolId, schoolId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> resultSet.getLong("class_list_id"), arguments);
    }

    /** The student's most recently enrolled class (by academic year), for display in report
     *  headers only - not an access-control boundary (that's already enforced by requiring a
     *  visible class_list_id before this is ever called). */
    public Optional<StudentClassContext> findCurrentClassContext(long studentId, String schoolId) {
        String sql = """
                SELECT grade.grade_level_name, section_row.section_name, academic_year.year_name
                  FROM class_lists class_list
                  JOIN classes class_row ON class_row.class_id = class_list.class_id
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = class_row.academic_year_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                  JOIN grade_levels grade ON grade.grade_level_id = section_row.grade_level_id
                 WHERE class_list.student_id = ?
                   AND academic_year.school_id = ?
                 ORDER BY academic_year.start_date DESC
                 LIMIT 1
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new StudentClassContext(
                resultSet.getString("grade_level_name"),
                resultSet.getString("section_name"),
                resultSet.getString("year_name")
        ), studentId, schoolId).stream().findFirst();
    }

    /** Display name of any user (teacher or principal), for report signature lines. */
    public Optional<String> findUserFullName(long userId) {
        String sql = """
                SELECT %s AS full_name
                  FROM users teacher
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = teacher.suffix_id
                 WHERE teacher.user_id = ?
                """.formatted(TEACHER_NAME_SQL);
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> resultSet.getString("full_name"), userId)
                .stream().findFirst();
    }

    /** The currently effective rule definition JSON for a metric scope: the school's own
     *  active rule set if it has one, otherwise the global default (school_id IS NULL). */
    public Optional<String> findActiveRuleDefinition(String schoolId, String metricScope) {
        String sql = """
                SELECT rule_definition
                  FROM performance_rule_sets
                 WHERE metric_scope = ?
                   AND rule_status = 'active'
                   AND (school_id = ? OR school_id IS NULL)
                   AND (effective_from_at IS NULL OR effective_from_at <= CURRENT_TIMESTAMP)
                   AND (effective_until_at IS NULL OR effective_until_at > CURRENT_TIMESTAMP)
                 ORDER BY school_id IS NULL, performance_rule_set_id DESC
                 LIMIT 1
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> resultSet.getString("rule_definition"),
                metricScope, schoolId).stream().findFirst();
    }

    /** Half-open time range [start, endExclusive) used to bound sync-activity queries. */
    public record TimeWindow(Instant start, Instant endExclusive) {
    }

    public Optional<TimeWindow> findTermPeriodWindow(int termPeriodId, String schoolId) {
        record CalendarWindow(int id, String name, int order, Instant start, Instant end) { }
        String sql = """
                SELECT term.term_period_id, term.term_name, term.term_order, term.start_at, term.end_at
                  FROM term_periods term
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = term.academic_year_id
                  JOIN term_periods selected_term
                    ON selected_term.academic_year_id = term.academic_year_id
                 WHERE selected_term.term_period_id = ?
                   AND academic_year.school_id = ?
                """;
        List<CalendarWindow> calendar = jdbcTemplate.query(sql, (resultSet, rowNumber) -> new CalendarWindow(
                resultSet.getInt("term_period_id"), resultSet.getString("term_name"), resultSet.getInt("term_order"),
                resultSet.getTimestamp("start_at").toInstant(),
                resultSet.getTimestamp("end_at").toInstant()
        ), termPeriodId, schoolId);
        boolean endExclusive = V3AcademicCalendarLayout.detect(calendar, CalendarWindow::name, CalendarWindow::order)
                .filter(layout -> layout == V3AcademicCalendarLayout.THREE_TERMS).isPresent();
        // Legacy quarters use an inclusive final second; new terms already store an exclusive end.
        return calendar.stream().filter(term -> term.id() == termPeriodId)
                .map(term -> new TimeWindow(term.start(), endExclusive ? term.end() : term.end().plusSeconds(1)))
                .findFirst();
    }

    public Optional<TimeWindow> findAcademicYearWindow(int academicYearId, String schoolId, ZoneId zone) {
        String sql = """
                SELECT academic_year.start_date, academic_year.end_date
                  FROM academic_years academic_year
                 WHERE academic_year.academic_year_id = ?
                   AND academic_year.school_id = ?
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new TimeWindow(
                resultSet.getDate("start_date").toLocalDate().atStartOfDay(zone).toInstant(),
                resultSet.getDate("end_date").toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        ), academicYearId, schoolId).stream().findFirst();
    }

    /** One row per (teacher, assessment) with at least one upload in the window, most recently
     *  active first.
     *  <p>syncs timestamps are always written by the database itself (CURRENT_TIMESTAMP), but the
     *  JDBC URL declares serverTimezone=UTC for a server running at +08:00, so reading them via
     *  getTimestamp() comes back 8 hours ahead. UNIX_TIMESTAMP / FROM_UNIXTIME work on the true
     *  instant and are immune to that mismatch. */
    public List<V3SyncActivityReportResponse.AssessmentSync> listSyncedAssessments(
            String schoolId,
            Long teacherUserId,
            Instant windowStart,
            Instant windowEnd
    ) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(schoolId);
        StringBuilder filters = new StringBuilder();
        if (teacherUserId != null) {
            filters.append(" AND sync.user_id = ?");
            arguments.add(teacherUserId);
        }
        if (windowStart != null) {
            filters.append(" AND sync.started_at >= FROM_UNIXTIME(?)");
            arguments.add(windowStart.getEpochSecond());
        }
        if (windowEnd != null) {
            filters.append(" AND sync.started_at < FROM_UNIXTIME(?)");
            arguments.add(windowEnd.getEpochSecond());
        }
        String sql = """
                SELECT sync.user_id,
                       %s AS teacher_name,
                       sync.test_assignment_id,
                       test.test_name,
                       CONCAT(grade.grade_level_name, ' - ', section_row.section_name) AS class_name,
                       UNIX_TIMESTAMP(MAX(CASE WHEN sync.sync_status = 'success'
                                               THEN sync.completed_at END)) AS last_synced_epoch,
                       COALESCE(MAX(failures.results_not_uploaded), 0) AS results_not_uploaded,
                       MAX(failures.error_details) AS error_details
                  FROM syncs sync
                  JOIN users teacher ON teacher.user_id = sync.user_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = teacher.suffix_id
                  JOIN test_assignments assignment ON assignment.test_assignment_id = sync.test_assignment_id
                  JOIN tests test ON test.test_id = assignment.test_id
                  JOIN class_assignments class_assignment
                    ON class_assignment.class_assignment_id = assignment.class_assignment_id
                  JOIN classes class_row ON class_row.class_id = class_assignment.class_id
                  JOIN sections section_row ON section_row.section_id = class_row.section_id
                  JOIN grade_levels grade ON grade.grade_level_id = section_row.grade_level_id
                  LEFT JOIN (
                      -- A result counts as not uploaded only if its LATEST attempt failed, so
                      -- failures a later retry fixed are ignored. Unwindowed on purpose: this is
                      -- the current state. syncs.error_message is never written, so the reasons
                      -- come from the items.
                      SELECT latest.test_assignment_id,
                             COUNT(*) AS results_not_uploaded,
                             GROUP_CONCAT(DISTINCT latest.error_message SEPARATOR '; ') AS error_details
                        FROM (
                            SELECT attempt.test_assignment_id, item.sync_status, item.error_message,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY attempt.test_assignment_id, item.result_uuid
                                       ORDER BY attempt.started_at DESC, item.sync_item_id DESC
                                   ) AS attempt_rank
                              FROM sync_items item
                              JOIN syncs attempt ON attempt.sync_id = item.sync_id
                             WHERE attempt.direction = 'upload'
                        ) latest
                       WHERE latest.attempt_rank = 1 AND latest.sync_status = 'failed'
                       GROUP BY latest.test_assignment_id
                  ) failures ON failures.test_assignment_id = sync.test_assignment_id
                 WHERE teacher.school_id = ?
                   AND sync.direction = 'upload'
                %s
                 GROUP BY sync.user_id, teacher.first_name, teacher.middle_name, teacher.last_name,
                          suffix.suffix_name, sync.test_assignment_id, test.test_name,
                          grade.grade_level_name, section_row.section_name
                 ORDER BY MAX(sync.started_at) DESC
                """.formatted(TEACHER_NAME_SQL, filters);
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new V3SyncActivityReportResponse.AssessmentSync(
                resultSet.getLong("user_id"),
                resultSet.getString("teacher_name"),
                resultSet.getLong("test_assignment_id"),
                resultSet.getString("test_name"),
                resultSet.getString("class_name"),
                epochInstant(resultSet, "last_synced_epoch"),
                resultSet.getInt("results_not_uploaded"),
                resultSet.getString("error_details")
        ), arguments.toArray());
    }

    public List<StudentAssessmentHistoryRow> listStudentAssessmentHistory(List<Long> classListIds, Long teacherUserId) {
        String placeholders = classListIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String teacherFilter = teacherUserId == null ? "" : " AND class_assignment.user_id = ?";
        List<Object> arguments = new ArrayList<>(classListIds);
        if (teacherUserId != null) arguments.add(teacherUserId);
        String sql = ("""
                SELECT test.test_id, test_assignment.test_assignment_id, test.test_name, test.term_period_id,
                       term_period.term_name,
                       result.result_status,
                       CASE WHEN result.result_status = 'finalized' THEN result.total_score END AS earned_points,
                       CASE WHEN result.result_status = 'finalized' THEN result.max_score END AS maximum_points,
                       CASE WHEN result.result_status = 'finalized' THEN result.percentage_snapshot END AS percentage,
                       CASE WHEN result.result_status = 'finalized' THEN result.performance_status END AS performance_status,
                       COALESCE(result.finalized_at, result.verification_completed_at, result.submitted_at) AS completed_at,
                       subject.subject_name,
                       test_assignment.open_at,
                       test_assignment.close_at
                  FROM test_results result
                  JOIN test_assignments test_assignment ON test_assignment.test_assignment_id = result.test_assignment_id
                  JOIN tests test ON test.test_id = test_assignment.test_id
                  JOIN term_periods term_period ON term_period.term_period_id = test.term_period_id
                  JOIN class_assignments class_assignment
                    ON class_assignment.class_assignment_id = test_assignment.class_assignment_id
                  JOIN subjects subject ON subject.subject_id = class_assignment.subject_id
                 WHERE result.class_list_id IN (%s)%s
                   AND result.result_status <> 'superseded'
                   AND NOT EXISTS (
                       SELECT 1 FROM test_results newer
                        WHERE newer.test_assignment_id = result.test_assignment_id
                          AND newer.class_list_id = result.class_list_id
                          AND newer.result_status <> 'superseded'
                          AND (newer.attempt_number > result.attempt_number
                               OR (newer.attempt_number = result.attempt_number
                                   AND newer.test_result_id > result.test_result_id))
                   )
                 ORDER BY completed_at DESC
                """).formatted(placeholders, teacherFilter);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new StudentAssessmentHistoryRow(
                        resultSet.getLong("test_id"),
                        resultSet.getLong("test_assignment_id"),
                        resultSet.getString("test_name"),
                        resultSet.getInt("term_period_id"),
                        resultSet.getString("term_name"),
                        resultSet.getString("result_status"),
                        resultSet.getBigDecimal("earned_points"),
                        resultSet.getBigDecimal("maximum_points"),
                        resultSet.getBigDecimal("percentage"),
                        nullableInstant(resultSet, "completed_at"),
                        resultSet.getString("subject_name"),
                        resultSet.getString("performance_status"),
                        nullableInstant(resultSet, "open_at"),
                        nullableInstant(resultSet, "close_at")
                ),
                arguments.toArray()
        );
    }

    public List<StudentSkillItemTotalRow> listStudentSkillItemTotals(List<Long> classListIds, Long teacherUserId) {
        String placeholders = classListIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String teacherFilter = teacherUserId == null ? "" : " AND class_assignment.user_id = ?";
        List<Object> arguments = new ArrayList<>(classListIds);
        if (teacherUserId != null) arguments.add(teacherUserId);
        String sql = ("""
                SELECT skill_id, competency_name,
                       COUNT(*) AS assessed_item_count,
                       COALESCE(SUM(maximum_points), 0) AS possible_points
                  FROM (
                      SELECT DISTINCT skill.skill_id, competency.competency_name,
                             question.question_id, question.maximum_points
                        FROM test_results result
                        JOIN test_assignments test_assignment
                          ON test_assignment.test_assignment_id = result.test_assignment_id
                        JOIN class_assignments class_assignment
                          ON class_assignment.class_assignment_id = test_assignment.class_assignment_id
                        JOIN test_parts part ON part.test_id = test_assignment.test_id
                        JOIN part_skill_mappings mapping ON mapping.test_part_id = part.test_part_id
                        JOIN skills skill ON skill.skill_id = mapping.skill_id
                        JOIN competency_tags competency ON competency.competency_id = skill.competency_id
                        JOIN questions question
                          ON question.test_part_id = mapping.test_part_id
                         AND question.item_number BETWEEN mapping.start_item_number AND mapping.end_item_number
                       WHERE result.class_list_id IN (%s)%s
                         AND result.result_status = 'finalized'
                  ) distinct_student_skill_questions
                 GROUP BY skill_id, competency_name
                """).formatted(placeholders, teacherFilter);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new StudentSkillItemTotalRow(
                        resultSet.getLong("skill_id"),
                        resultSet.getString("competency_name"),
                        resultSet.getInt("assessed_item_count"),
                        resultSet.getBigDecimal("possible_points")
                ),
                arguments.toArray()
        );
    }

    public List<StudentSkillAnswerTotalRow> listStudentSkillAnswerTotals(List<Long> classListIds, Long teacherUserId) {
        String placeholders = classListIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String teacherFilter = teacherUserId == null ? "" : " AND class_assignment.user_id = ?";
        List<Object> arguments = new ArrayList<>(classListIds);
        if (teacherUserId != null) arguments.add(teacherUserId);
        String sql = ("""
                SELECT skill_id, COALESCE(SUM(points_earned), 0) AS earned_points
                  FROM (
                      SELECT DISTINCT skill.skill_id, answer_row.test_result_id, answer_row.question_id,
                             answer_row.points_earned
                        FROM test_results result
                        JOIN test_assignments test_assignment
                          ON test_assignment.test_assignment_id = result.test_assignment_id
                        JOIN class_assignments class_assignment
                          ON class_assignment.class_assignment_id = test_assignment.class_assignment_id
                        JOIN test_parts part ON part.test_id = test_assignment.test_id
                        JOIN part_skill_mappings mapping ON mapping.test_part_id = part.test_part_id
                        JOIN skills skill ON skill.skill_id = mapping.skill_id
                        JOIN questions question
                          ON question.test_part_id = mapping.test_part_id
                         AND question.item_number BETWEEN mapping.start_item_number AND mapping.end_item_number
                        JOIN student_answers answer_row
                          ON answer_row.question_id = question.question_id
                         AND answer_row.test_result_id = result.test_result_id
                       WHERE result.class_list_id IN (%s)%s
                         AND result.result_status = 'finalized'
                  ) distinct_student_skill_answers
                 GROUP BY skill_id
                """).formatted(placeholders, teacherFilter);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new StudentSkillAnswerTotalRow(
                        resultSet.getLong("skill_id"),
                        resultSet.getBigDecimal("earned_points")
                ),
                arguments.toArray()
        );
    }

    // ---------------------------------------------------------------------------------
    // Learning Competency (Term -> Grade Level -> Subject -> Root -> Skill -> students)
    // ---------------------------------------------------------------------------------

    public Optional<CompetencyScopeRow> findCompetencyScope(
            String schoolId, int termPeriodId, int gradeLevelId, int subjectId
    ) {
        String sql = """
                SELECT school.school_id,
                       school.school_name,
                       academic_year.academic_year_id,
                       academic_year.year_name AS academic_year_name,
                       term.term_period_id,
                       term.term_name,
                       (SELECT grade.grade_level_name FROM grade_levels grade
                         WHERE grade.grade_level_id = ?) AS grade_level_name,
                       (SELECT subject.subject_name FROM subjects subject
                         WHERE subject.subject_id = ?) AS subject_name
                  FROM term_periods term
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = term.academic_year_id
                  JOIN school_profiles school ON school.school_id = academic_year.school_id
                 WHERE term.term_period_id = ?
                   AND academic_year.school_id = ?
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new CompetencyScopeRow(
                resultSet.getString("school_id"),
                resultSet.getString("school_name"),
                resultSet.getInt("academic_year_id"),
                resultSet.getString("academic_year_name"),
                resultSet.getInt("term_period_id"),
                resultSet.getString("term_name"),
                resultSet.getString("grade_level_name"),
                resultSet.getString("subject_name")
        ), gradeLevelId, subjectId, termPeriodId, schoolId).stream().findFirst();
    }

    /** Shared by both Learning Competency queries: finalized results of every assessment
     *  given in the term to the grade level for the subject, optionally only one teacher's. */
    private static final String COMPETENCY_RESULT_SCOPE = """
              FROM test_results result
              JOIN class_lists class_list ON class_list.class_list_id = result.class_list_id
              JOIN test_assignments test_assignment
                ON test_assignment.test_assignment_id = result.test_assignment_id
              JOIN class_assignments assignment
                ON assignment.class_assignment_id = test_assignment.class_assignment_id
              JOIN classes class_row ON class_row.class_id = assignment.class_id
              JOIN sections section_row ON section_row.section_id = class_row.section_id
              JOIN tests test ON test.test_id = test_assignment.test_id
            """;

    private static final String COMPETENCY_RESULT_FILTER = """
             WHERE result.result_status = 'finalized'
               AND test.school_id = ?
               AND test.term_period_id = ?
               AND section_row.grade_level_id = ?
               AND assignment.subject_id = ?
            """;

    public List<V3LearningCompetencyReportResponse.IncludedAssessment> listCompetencyAssessments(
            String schoolId, int termPeriodId, int gradeLevelId, int subjectId, Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" : " AND assignment.user_id = ?";
        String sql = "SELECT DISTINCT test.test_id, test.test_name "
                + COMPETENCY_RESULT_SCOPE
                + COMPETENCY_RESULT_FILTER
                + teacherFilter
                + " ORDER BY test.test_name, test.test_id";
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId, termPeriodId, gradeLevelId, subjectId}
                : new Object[]{schoolId, termPeriodId, gradeLevelId, subjectId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) ->
                new V3LearningCompetencyReportResponse.IncludedAssessment(
                        resultSet.getLong("test_id"),
                        resultSet.getString("test_name")
                ), arguments);
    }

    /** One row per (skill, student). The innermost DISTINCT is one row per (skill, result,
     *  question), so a question covered by two overlapping mapping ranges is counted once, and
     *  possible points come from the questions themselves - an unanswered item still counts
     *  toward the maximum with 0 earned. */
    public List<StudentCompetencyTotalRow> listStudentCompetencyTotals(
            String schoolId, int termPeriodId, int gradeLevelId, int subjectId, Long teacherUserId
    ) {
        String teacherFilter = teacherUserId == null ? "" : " AND assignment.user_id = ?";
        String sql = ("""
                SELECT totals.root_tag_id, totals.root_tag_name, totals.skill_id, totals.competency_id,
                       totals.competency_name, totals.student_id, %s AS full_name, totals.section_name,
                       totals.assessment_count, totals.possible_points, totals.earned_points
                  FROM (
                      SELECT root_tag_id, root_tag_name, skill_id, competency_id, competency_name, student_id,
                             MAX(section_name) AS section_name,
                             COUNT(DISTINCT test_result_id) AS assessment_count,
                             COALESCE(SUM(maximum_points), 0) AS possible_points,
                             COALESCE(SUM(points_earned), 0) AS earned_points
                        FROM (
                            SELECT DISTINCT root_tag.root_tag_id, root_tag.root_tag_name, skill.skill_id,
                                   competency.competency_id, competency.competency_name,
                                   class_list.student_id, section_row.section_name, result.test_result_id,
                                   question.question_id, question.maximum_points,
                                   COALESCE(answer_row.points_earned, 0) AS points_earned
                """
                + COMPETENCY_RESULT_SCOPE
                + """
                              JOIN test_parts part ON part.test_id = test.test_id
                              JOIN part_skill_mappings mapping ON mapping.test_part_id = part.test_part_id
                              JOIN skills skill ON skill.skill_id = mapping.skill_id
                              JOIN competency_tags competency ON competency.competency_id = skill.competency_id
                              JOIN root_tags root_tag ON root_tag.root_tag_id = competency.root_tag_id
                              JOIN questions question
                                ON question.test_part_id = mapping.test_part_id
                               AND question.item_number BETWEEN mapping.start_item_number AND mapping.end_item_number
                              LEFT JOIN student_answers answer_row
                                ON answer_row.test_result_id = result.test_result_id
                               AND answer_row.question_id = question.question_id
                """
                + COMPETENCY_RESULT_FILTER
                + teacherFilter
                + """
                        ) distinct_answers
                       GROUP BY root_tag_id, root_tag_name, skill_id, competency_id, competency_name, student_id
                  ) totals
                  JOIN students student ON student.student_id = totals.student_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = student.suffix_id
                """).formatted(STUDENT_NAME_SQL);
        Object[] arguments = teacherUserId == null
                ? new Object[]{schoolId, termPeriodId, gradeLevelId, subjectId}
                : new Object[]{schoolId, termPeriodId, gradeLevelId, subjectId, teacherUserId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new StudentCompetencyTotalRow(
                resultSet.getLong("root_tag_id"),
                resultSet.getString("root_tag_name"),
                resultSet.getLong("skill_id"),
                resultSet.getLong("competency_id"),
                resultSet.getString("competency_name"),
                resultSet.getLong("student_id"),
                resultSet.getString("full_name"),
                resultSet.getString("section_name"),
                resultSet.getInt("assessment_count"),
                resultSet.getBigDecimal("possible_points"),
                resultSet.getBigDecimal("earned_points")
        ), arguments);
    }

    private static List<Long> parseSkillIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String value : csv.split(",")) {
            ids.add(Long.valueOf(value.trim()));
        }
        return List.copyOf(ids);
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Instant epochInstant(ResultSet resultSet, String column) throws SQLException {
        long epochSeconds = resultSet.getLong(column);
        return resultSet.wasNull() ? null : Instant.ofEpochSecond(epochSeconds);
    }

    private static Instant requiredInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }
}
