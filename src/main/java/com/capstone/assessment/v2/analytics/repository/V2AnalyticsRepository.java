package com.capstone.assessment.v2.analytics.repository;

import com.capstone.assessment.v2.analytics.dto.V2AssessmentTrendResponse;
import com.capstone.assessment.v2.analytics.dto.V2GradeMasteryResponse;
import com.capstone.assessment.v2.analytics.dto.V2ItemAnalysisResponse;
import com.capstone.assessment.v2.analytics.dto.V2LmsResponse;
import com.capstone.assessment.v2.analytics.dto.V2SyncActivityResponse;
import com.capstone.assessment.v2.analytics.dto.V2TestPartResultResponse;
import com.capstone.assessment.v2.analytics.model.V2AnalyticsTestContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Profile("v2")
@Repository
public class V2AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public V2AnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<V2AnalyticsTestContext> findTestContext(long testId) {
        return jdbcTemplate.query(
                """
                SELECT t.test_id,
                       ca.user_id AS teacher_user_id,
                       u.school_id,
                       t.status
                  FROM tests t
                  JOIN class_assignments ca ON ca.class_assignment_id = t.class_assignment_id
                  JOIN users u ON u.user_id = ca.user_id
                 WHERE t.test_id = ?
                """,
                (rs, rowNum) -> new V2AnalyticsTestContext(
                        rs.getLong("test_id"),
                        rs.getLong("teacher_user_id"),
                        rs.getString("school_id"),
                        rs.getString("status")
                ),
                testId
        ).stream().findFirst();
    }

    public boolean testPartBelongsToTest(long testId, long testPartId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_parts WHERE test_id = ? AND test_part_id = ?",
                Integer.class,
                testId,
                testPartId
        );
        return count != null && count > 0;
    }

    public List<V2LmsResponse> listLms(long testId) {
        return jdbcTemplate.query(
                """
                SELECT sk.skill_id,
                       ct.competency_id,
                       ct.competency_name,
                       ROUND(SUM(sa.points_earned), 2) AS earned_points,
                       ROUND(SUM(tp.points_per_item), 2) AS possible_points,
                       ROUND(
                           CASE
                               WHEN SUM(tp.points_per_item) = 0 THEN 0
                               ELSE SUM(sa.points_earned) * 100.0 / SUM(tp.points_per_item)
                           END,
                           2
                       ) AS mastery_rate,
                       COUNT(DISTINCT tr.test_result_id) AS respondent_count,
                       COUNT(DISTINCT CASE WHEN sa.is_correct = 0 THEN tr.class_list_id END) AS affected_students
                  FROM test_results tr
                  JOIN student_answers sa ON sa.test_result_id = tr.test_result_id
                  JOIN questions q ON q.question_id = sa.question_id
                  JOIN test_parts tp
                    ON tp.test_part_id = q.test_part_id
                   AND tp.test_id = tr.test_id
                  JOIN mappings m ON m.question_id = q.question_id
                  JOIN skills sk ON sk.skill_id = m.skill_id
                  JOIN competency_tags ct ON ct.competency_id = sk.competency_id
                 WHERE tr.test_id = ?
                   AND sa.verified_at IS NOT NULL
                 GROUP BY sk.skill_id, ct.competency_id, ct.competency_name
                 ORDER BY mastery_rate ASC, ct.competency_name ASC
                """,
                (rs, rowNum) -> new V2LmsResponse(
                        rs.getLong("skill_id"),
                        rs.getLong("competency_id"),
                        rs.getString("competency_name"),
                        rs.getBigDecimal("earned_points"),
                        rs.getBigDecimal("possible_points"),
                        rs.getBigDecimal("mastery_rate"),
                        null,
                        rs.getInt("respondent_count"),
                        rs.getInt("affected_students")
                ),
                testId
        );
    }

    public List<V2ItemAnalysisResponse> listItemAnalysis(long testId) {
        return jdbcTemplate.query(
                """
                SELECT q.question_id,
                       tp.test_part_id,
                       q.item_number,
                       MIN(sk.skill_id) AS skill_id,
                       MIN(ct.competency_id) AS competency_id,
                       GROUP_CONCAT(DISTINCT ct.competency_name ORDER BY ct.competency_name SEPARATOR ', ') AS competency_name,
                       COUNT(DISTINCT CASE WHEN sa.is_correct = 1 THEN sa.student_answer_id END) AS correct_responses,
                       COUNT(DISTINCT sa.student_answer_id) AS total_responses,
                       ROUND(
                           CASE
                               WHEN COUNT(DISTINCT sa.student_answer_id) = 0 THEN 0
                               ELSE COUNT(DISTINCT CASE WHEN sa.is_correct = 1 THEN sa.student_answer_id END) * 100.0
                                    / COUNT(DISTINCT sa.student_answer_id)
                           END,
                           2
                       ) AS correctness_percentage
                  FROM test_parts tp
                  JOIN questions q ON q.test_part_id = tp.test_part_id
                  LEFT JOIN mappings m ON m.question_id = q.question_id
                  LEFT JOIN skills sk ON sk.skill_id = m.skill_id
                  LEFT JOIN competency_tags ct ON ct.competency_id = sk.competency_id
                  LEFT JOIN test_results tr ON tr.test_id = tp.test_id
                  LEFT JOIN student_answers sa
                    ON sa.test_result_id = tr.test_result_id
                   AND sa.question_id = q.question_id
                   AND sa.verified_at IS NOT NULL
                 WHERE tp.test_id = ?
                 GROUP BY q.question_id, tp.test_part_id, q.item_number
                 ORDER BY tp.part_order, q.item_number
                """,
                (rs, rowNum) -> new V2ItemAnalysisResponse(
                        rs.getLong("question_id"),
                        rs.getLong("test_part_id"),
                        rs.getInt("item_number"),
                        nullableLong(rs.getObject("skill_id")),
                        nullableLong(rs.getObject("competency_id")),
                        rs.getString("competency_name"),
                        rs.getInt("correct_responses"),
                        rs.getInt("total_responses"),
                        rs.getBigDecimal("correctness_percentage"),
                        null
                ),
                testId
        );
    }

    public List<V2TestPartResultResponse> listTestPartResults(long testId, long testPartId) {
        return jdbcTemplate.query(
                """
                SELECT st.student_id,
                       CONCAT_WS(' ', st.first_name, NULLIF(st.middle_name, ''), st.last_name, NULLIF(st.suffix, '')) AS student_name,
                       st.student_lrn,
                       tr.test_id,
                       tp.test_part_id,
                       ROUND(COALESCE(SUM(sa.points_earned), 0), 2) AS part_score,
                       ROUND(tp.number_of_items * tp.points_per_item, 2) AS max_score,
                       ROUND(
                           CASE
                               WHEN tp.number_of_items * tp.points_per_item = 0 THEN 0
                               ELSE COALESCE(SUM(sa.points_earned), 0) * 100.0
                                    / (tp.number_of_items * tp.points_per_item)
                           END,
                           2
                       ) AS percentage,
                       tr.checked_at,
                       (
                           SELECT MAX(si.synced_at)
                             FROM sync_items si
                            WHERE si.test_result_id = tr.test_result_id
                              AND si.sync_status = 'success'
                       ) AS synced_at
                  FROM test_results tr
                  JOIN class_lists cl ON cl.class_list_id = tr.class_list_id
                  JOIN students st ON st.student_id = cl.student_id
                  JOIN test_parts tp
                    ON tp.test_id = tr.test_id
                   AND tp.test_part_id = ?
                  JOIN questions q ON q.test_part_id = tp.test_part_id
                  LEFT JOIN student_answers sa
                    ON sa.test_result_id = tr.test_result_id
                   AND sa.question_id = q.question_id
                   AND sa.verified_at IS NOT NULL
                 WHERE tr.test_id = ?
                 GROUP BY st.student_id,
                          st.student_lrn,
                          st.first_name,
                          st.middle_name,
                          st.last_name,
                          st.suffix,
                          tr.test_result_id,
                          tr.test_id,
                          tp.test_part_id,
                          tp.number_of_items,
                          tp.points_per_item,
                          tr.checked_at
                 ORDER BY st.last_name ASC, st.first_name ASC, st.student_id ASC
                """,
                (rs, rowNum) -> new V2TestPartResultResponse(
                        rs.getLong("student_id"),
                        rs.getString("student_name"),
                        rs.getString("student_lrn"),
                        rs.getLong("test_id"),
                        rs.getLong("test_part_id"),
                        rs.getBigDecimal("part_score"),
                        rs.getBigDecimal("max_score"),
                        rs.getBigDecimal("percentage"),
                        null,
                        toInstant(rs.getTimestamp("checked_at")),
                        toInstant(rs.getTimestamp("synced_at"))
                ),
                testPartId,
                testId
        );
    }

    public boolean teacherBelongsToSchool(long teacherUserId, String schoolId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM users u
                  JOIN roles r ON r.role_id = u.role_id
                 WHERE u.user_id = ?
                   AND u.school_id = ?
                   AND r.role_name = 'teacher'
                """,
                Integer.class,
                teacherUserId,
                schoolId
        );
        return count != null && count > 0;
    }

    public List<V2SyncActivityResponse> listSyncActivity(
            String schoolId,
            Long gradeLevelId,
            Long sectionId,
            Long teacherUserId,
            Long subjectId,
            Long classId
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT sy.sync_id,
                       sy.sync_uuid,
                       sy.user_id AS teacher_user_id,
                       CONCAT_WS(' ', u.first_name, NULLIF(u.middle_name, ''), u.last_name, NULLIF(u.suffix, '')) AS teacher_name,
                       sy.test_id,
                       t.test_name,
                       ca.class_id,
                       gl.grade_level_name,
                       sec.section_name,
                       sub.subject_name,
                       sy.sync_status,
                       COUNT(DISTINCT CASE WHEN si.sync_status = 'success' THEN si.test_result_id END) AS successful_results,
                       COUNT(DISTINCT CASE WHEN si.sync_status = 'failed' THEN si.sync_item_id END) AS failed_results,
                       COUNT(DISTINCT CASE WHEN si.sync_status = 'skipped' THEN si.sync_item_id END) AS skipped_results,
                       sy.started_at,
                       sy.completed_at,
                       COALESCE(MAX(si.synced_at), sy.completed_at, sy.started_at) AS last_synced_at
                  FROM syncs sy
                  JOIN users u ON u.user_id = sy.user_id
                  LEFT JOIN tests t ON t.test_id = sy.test_id
                  LEFT JOIN class_assignments ca
                    ON ca.class_assignment_id = t.class_assignment_id
                   AND ca.user_id = sy.user_id
                  LEFT JOIN classes c ON c.class_id = ca.class_id
                  LEFT JOIN sections sec ON sec.section_id = c.section_id
                  LEFT JOIN grade_levels gl ON gl.grade_level_id = sec.grade_level_id
                  LEFT JOIN subjects sub ON sub.subject_id = ca.subject_id
                  LEFT JOIN sync_items si ON si.sync_id = sy.sync_id
                 WHERE u.school_id = ?
                   AND sy.direction = 'upload'
                """);
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        appendFilters(sql, args, gradeLevelId, sectionId, teacherUserId, subjectId, classId, "sy.user_id");
        sql.append("""
                 GROUP BY sy.sync_id,
                          sy.sync_uuid,
                          sy.user_id,
                          u.first_name,
                          u.middle_name,
                          u.last_name,
                          u.suffix,
                          sy.test_id,
                          t.test_name,
                          ca.class_id,
                          gl.grade_level_name,
                          sec.section_name,
                          sub.subject_name,
                          sy.sync_status,
                          sy.started_at,
                          sy.completed_at
                 ORDER BY last_synced_at DESC, sy.sync_id DESC
                 LIMIT 20
                """);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new V2SyncActivityResponse(
                        rs.getLong("sync_id"),
                        rs.getString("sync_uuid"),
                        rs.getLong("teacher_user_id"),
                        rs.getString("teacher_name"),
                        nullableLong(rs.getObject("test_id")),
                        rs.getString("test_name"),
                        nullableLong(rs.getObject("class_id")),
                        rs.getString("grade_level_name"),
                        rs.getString("section_name"),
                        rs.getString("subject_name"),
                        rs.getString("sync_status"),
                        rs.getInt("successful_results"),
                        rs.getInt("failed_results"),
                        rs.getInt("skipped_results"),
                        toInstant(rs.getTimestamp("started_at")),
                        toInstant(rs.getTimestamp("completed_at")),
                        toInstant(rs.getTimestamp("last_synced_at"))
                ),
                args.toArray()
        );
    }

    public int countSchoolAssessments(
            String schoolId,
            Long gradeLevelId,
            Long sectionId,
            Long teacherUserId,
            Long subjectId,
            Long classId
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT t.test_id)
                  FROM tests t
                  JOIN class_assignments ca ON ca.class_assignment_id = t.class_assignment_id
                  JOIN users u ON u.user_id = ca.user_id
                  JOIN classes c ON c.class_id = ca.class_id
                  JOIN sections sec ON sec.section_id = c.section_id
                  JOIN grade_levels gl ON gl.grade_level_id = sec.grade_level_id
                 WHERE u.school_id = ?
                   AND t.status <> 'archived'
                """);
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        appendFilters(sql, args, gradeLevelId, sectionId, teacherUserId, subjectId, classId, "ca.user_id");
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    public List<V2LmsResponse> listSchoolLms(
            String schoolId,
            Long gradeLevelId,
            Long sectionId,
            Long teacherUserId,
            Long subjectId,
            Long classId
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT sk.skill_id,
                       ct.competency_id,
                       ct.competency_name,
                       ROUND(SUM(sa.points_earned), 2) AS earned_points,
                       ROUND(SUM(tp.points_per_item), 2) AS possible_points,
                       ROUND(
                           CASE
                               WHEN SUM(tp.points_per_item) = 0 THEN 0
                               ELSE SUM(sa.points_earned) * 100.0 / SUM(tp.points_per_item)
                           END,
                           2
                       ) AS mastery_rate,
                       COUNT(DISTINCT tr.test_result_id) AS respondent_count,
                       COUNT(DISTINCT CASE WHEN sa.is_correct = 0 THEN tr.class_list_id END) AS affected_students
                  FROM test_results tr
                  JOIN tests t ON t.test_id = tr.test_id
                  JOIN class_assignments ca ON ca.class_assignment_id = t.class_assignment_id
                  JOIN users u ON u.user_id = ca.user_id
                  JOIN classes c ON c.class_id = ca.class_id
                  JOIN sections sec ON sec.section_id = c.section_id
                  JOIN grade_levels gl ON gl.grade_level_id = sec.grade_level_id
                  JOIN student_answers sa
                    ON sa.test_result_id = tr.test_result_id
                   AND sa.verified_at IS NOT NULL
                  JOIN questions q ON q.question_id = sa.question_id
                  JOIN test_parts tp
                    ON tp.test_part_id = q.test_part_id
                   AND tp.test_id = tr.test_id
                  JOIN mappings m ON m.question_id = q.question_id
                  JOIN skills sk ON sk.skill_id = m.skill_id
                  JOIN competency_tags ct ON ct.competency_id = sk.competency_id
                 WHERE u.school_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        appendFilters(sql, args, gradeLevelId, sectionId, teacherUserId, subjectId, classId, "ca.user_id");
        sql.append("""
                 GROUP BY sk.skill_id, ct.competency_id, ct.competency_name
                 ORDER BY mastery_rate ASC, ct.competency_name ASC
                """);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new V2LmsResponse(
                        rs.getLong("skill_id"),
                        rs.getLong("competency_id"),
                        rs.getString("competency_name"),
                        rs.getBigDecimal("earned_points"),
                        rs.getBigDecimal("possible_points"),
                        rs.getBigDecimal("mastery_rate"),
                        null,
                        rs.getInt("respondent_count"),
                        rs.getInt("affected_students")
                ),
                args.toArray()
        );
    }

    public List<V2GradeMasteryResponse> listGradeMastery(
            String schoolId,
            Long gradeLevelId,
            Long sectionId,
            Long teacherUserId,
            Long subjectId,
            Long classId
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT gl.grade_level_id,
                       gl.grade_level_name,
                       ROUND(SUM(sa.points_earned), 2) AS earned_points,
                       ROUND(SUM(tp.points_per_item), 2) AS possible_points,
                       ROUND(
                           CASE
                               WHEN SUM(tp.points_per_item) = 0 THEN 0
                               ELSE SUM(sa.points_earned) * 100.0 / SUM(tp.points_per_item)
                           END,
                           2
                       ) AS mastery_rate,
                       COUNT(DISTINCT tr.test_result_id) AS respondent_count
                  FROM test_results tr
                  JOIN tests t ON t.test_id = tr.test_id
                  JOIN class_assignments ca ON ca.class_assignment_id = t.class_assignment_id
                  JOIN users u ON u.user_id = ca.user_id
                  JOIN classes c ON c.class_id = ca.class_id
                  JOIN sections sec ON sec.section_id = c.section_id
                  JOIN grade_levels gl ON gl.grade_level_id = sec.grade_level_id
                  JOIN student_answers sa
                    ON sa.test_result_id = tr.test_result_id
                   AND sa.verified_at IS NOT NULL
                  JOIN questions q ON q.question_id = sa.question_id
                  JOIN test_parts tp
                    ON tp.test_part_id = q.test_part_id
                   AND tp.test_id = tr.test_id
                 WHERE u.school_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        appendFilters(sql, args, gradeLevelId, sectionId, teacherUserId, subjectId, classId, "ca.user_id");
        sql.append("""
                 GROUP BY gl.grade_level_id, gl.grade_level_name
                 ORDER BY gl.grade_level_id
                """);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new V2GradeMasteryResponse(
                        rs.getLong("grade_level_id"),
                        rs.getString("grade_level_name"),
                        rs.getBigDecimal("earned_points"),
                        rs.getBigDecimal("possible_points"),
                        rs.getBigDecimal("mastery_rate"),
                        rs.getInt("respondent_count")
                ),
                args.toArray()
        );
    }

    public List<V2AssessmentTrendResponse> listAssessmentTrends(
            String schoolId,
            Long gradeLevelId,
            Long sectionId,
            Long teacherUserId,
            Long subjectId,
            Long classId
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.test_id,
                       t.test_name,
                       t.test_date,
                       ROUND(
                           CASE
                               WHEN SUM(tp.points_per_item) = 0 THEN 0
                               ELSE SUM(sa.points_earned) * 100.0 / SUM(tp.points_per_item)
                           END,
                           2
                       ) AS mastery_rate,
                       COUNT(DISTINCT tr.test_result_id) AS respondent_count
                  FROM test_results tr
                  JOIN tests t ON t.test_id = tr.test_id
                  JOIN class_assignments ca ON ca.class_assignment_id = t.class_assignment_id
                  JOIN users u ON u.user_id = ca.user_id
                  JOIN classes c ON c.class_id = ca.class_id
                  JOIN sections sec ON sec.section_id = c.section_id
                  JOIN grade_levels gl ON gl.grade_level_id = sec.grade_level_id
                  JOIN student_answers sa
                    ON sa.test_result_id = tr.test_result_id
                   AND sa.verified_at IS NOT NULL
                  JOIN questions q ON q.question_id = sa.question_id
                  JOIN test_parts tp
                    ON tp.test_part_id = q.test_part_id
                   AND tp.test_id = tr.test_id
                 WHERE u.school_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        appendFilters(sql, args, gradeLevelId, sectionId, teacherUserId, subjectId, classId, "ca.user_id");
        sql.append("""
                 GROUP BY t.test_id, t.test_name, t.test_date
                 ORDER BY t.test_date ASC, t.test_id ASC
                """);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new V2AssessmentTrendResponse(
                        rs.getLong("test_id"),
                        rs.getString("test_name"),
                        rs.getDate("test_date").toLocalDate(),
                        rs.getBigDecimal("mastery_rate"),
                        rs.getInt("respondent_count")
                ),
                args.toArray()
        );
    }

    private void appendFilters(
            StringBuilder sql,
            List<Object> args,
            Long gradeLevelId,
            Long sectionId,
            Long teacherUserId,
            Long subjectId,
            Long classId,
            String teacherColumn
    ) {
        appendFilter(sql, args, "gl.grade_level_id", gradeLevelId);
        appendFilter(sql, args, "sec.section_id", sectionId);
        appendFilter(sql, args, teacherColumn, teacherUserId);
        appendFilter(sql, args, "ca.subject_id", subjectId);
        appendFilter(sql, args, "ca.class_id", classId);
    }

    private void appendFilter(StringBuilder sql, List<Object> args, String column, Long value) {
        if (value == null) {
            return;
        }
        sql.append(" AND ").append(column).append(" = ?\n");
        args.add(value);
    }

    private Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
