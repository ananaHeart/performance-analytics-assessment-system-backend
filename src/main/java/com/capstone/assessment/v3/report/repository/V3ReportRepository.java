package com.capstone.assessment.v3.report.repository;

import com.capstone.assessment.v3.report.dto.V3ReportReferenceDataResponse;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentResultRow;
import com.capstone.assessment.v3.report.model.V3ReportModels.AssessmentScopeRow;
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

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Instant requiredInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }
}
