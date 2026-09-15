package com.capstone.assessment.v3.scoring.repository;

import com.capstone.assessment.v3.scoring.model.V3ScoringModels.PerformanceRuleSet;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.ResultContext;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.RubricScoreSummary;
import com.capstone.assessment.v3.scoring.model.V3ScoringModels.ScoringRow;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Profile("v3")
@Repository
public class V3ScoringRepository {

    private final JdbcTemplate jdbcTemplate;

    public V3ScoringRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ResultContext> findResultContextForUpdate(long testResultId) {
        List<ResultContext> rows = jdbcTemplate.query(
                """
                SELECT tr.test_result_id,
                       tr.result_uuid,
                       tr.test_assignment_id,
                       ta.test_id,
                       tr.class_list_id,
                       cl.student_id,
                       CONCAT_WS(' ', s.first_name, NULLIF(s.middle_name, ''), s.last_name) AS student_name,
                       tr.attempt_number,
                       tr.total_score,
                       tr.max_score,
                       tr.items_evaluated,
                       tr.result_status,
                       tr.percentage_snapshot,
                       tr.performance_status,
                       tr.performance_rule_set_id,
                       tr.score_version,
                       tr.scored_at,
                       ca.user_id AS teacher_user_id,
                       t.school_id AS test_school_id,
                       s.school_id AS student_school_id,
                       ca.class_id AS assignment_class_id,
                       cl.class_id AS membership_class_id,
                       t.status AS test_status,
                       ta.assignment_status AS test_assignment_status
                  FROM test_results tr
                  JOIN test_assignments ta ON ta.test_assignment_id = tr.test_assignment_id
                  JOIN tests t ON t.test_id = ta.test_id
                  JOIN class_assignments ca ON ca.class_assignment_id = ta.class_assignment_id
                  JOIN class_lists cl ON cl.class_list_id = tr.class_list_id
                  JOIN students s ON s.student_id = cl.student_id
                 WHERE tr.test_result_id = ?
                 FOR UPDATE
                """,
                (resultSet, rowNumber) -> mapResultContext(resultSet),
                testResultId
        );
        return rows.stream().findFirst();
    }

    public List<ScoringRow> findScoringRowsForUpdate(long testId, long testResultId) {
        return jdbcTemplate.query(
                """
                SELECT tp.test_part_id,
                       tp.part_order,
                       tp.part_name,
                       q.question_id,
                       q.item_number,
                       qt.question_type_code,
                       q.maximum_points,
                       q.rubric_id AS question_rubric_id,
                       ak.answer_key_id,
                       ak.answer_key_type,
                       ak.correct_question_option_id,
                       ak.rubric_id AS answer_key_rubric_id,
                       sa.student_answer_id,
                       sa.answer_uuid,
                       sa.selected_question_option_id,
                       selected_option.question_id AS selected_option_question_id,
                       sa.response_text,
                       (SELECT COUNT(*)
                          FROM answer_attachments attachment
                         WHERE attachment.student_answer_id = sa.student_answer_id
                           AND attachment.attachment_type IN ('answer_crop', 'teacher_evidence'))
                           AS written_evidence_attachment_count,
                       sa.answer_status,
                       sa.evaluation_status,
                       sa.verified_by_user_id,
                       sa.verified_at,
                       sa.is_correct,
                       sa.points_earned,
                       sa.finalized_at,
                       sa.score_version AS answer_score_version
                  FROM test_parts tp
                  JOIN questions q ON q.test_part_id = tp.test_part_id
                  JOIN question_types qt ON qt.question_type_id = q.question_type_id
                  LEFT JOIN answer_keys ak ON ak.question_id = q.question_id
                  LEFT JOIN student_answers sa
                    ON sa.question_id = q.question_id
                   AND sa.test_result_id = ?
                  LEFT JOIN question_options selected_option
                    ON selected_option.question_option_id = sa.selected_question_option_id
                 WHERE tp.test_id = ?
                 ORDER BY tp.part_order, q.item_number, q.question_id
                 FOR UPDATE
                """,
                (resultSet, rowNumber) -> mapScoringRow(resultSet),
                testResultId,
                testId
        );
    }

    public long countAnswersOutsideTest(long testResultId, long testId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM student_answers sa
                  JOIN questions q ON q.question_id = sa.question_id
                  JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                 WHERE sa.test_result_id = ?
                   AND tp.test_id <> ?
                """,
                Long.class,
                testResultId,
                testId
        );
        return count == null ? 0L : count;
    }

    public RubricScoreSummary summarizeRubricScores(
            long studentAnswerId,
            int scoreVersion,
            long rubricId
    ) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(rc.rubric_criterion_id) AS total_criteria,
                       COALESCE(SUM(CASE WHEN rc.is_required = 1 THEN 1 ELSE 0 END), 0) AS required_criteria,
                       COALESCE(SUM(CASE WHEN ars.answer_rubric_score_id IS NOT NULL THEN 1 ELSE 0 END), 0)
                           AS scored_criteria,
                       COALESCE(SUM(CASE
                           WHEN rc.is_required = 1 AND ars.answer_rubric_score_id IS NOT NULL THEN 1
                           ELSE 0
                       END), 0) AS scored_required_criteria,
                       COALESCE(SUM(CASE
                           WHEN ars.points_awarded > rc.maximum_points THEN 1
                           ELSE 0
                       END), 0) AS excessive_score_count,
                       COALESCE(SUM(ars.points_awarded), 0.00) AS awarded_points
                  FROM rubric_criteria rc
                  LEFT JOIN answer_rubric_scores ars
                    ON ars.rubric_criterion_id = rc.rubric_criterion_id
                   AND ars.student_answer_id = ?
                   AND ars.score_version = ?
                 WHERE rc.rubric_id = ?
                """,
                (resultSet, rowNumber) -> new RubricScoreSummary(
                        resultSet.getLong("total_criteria"),
                        resultSet.getLong("required_criteria"),
                        resultSet.getLong("scored_criteria"),
                        resultSet.getLong("scored_required_criteria"),
                        resultSet.getLong("excessive_score_count"),
                        resultSet.getBigDecimal("awarded_points")
                ),
                studentAnswerId,
                scoreVersion,
                rubricId
        );
    }

    public long countRubricScoresOutsideRubric(
            long studentAnswerId,
            int scoreVersion,
            long rubricId
    ) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM answer_rubric_scores ars
                  JOIN rubric_criteria rc
                    ON rc.rubric_criterion_id = ars.rubric_criterion_id
                 WHERE ars.student_answer_id = ?
                   AND ars.score_version = ?
                   AND rc.rubric_id <> ?
                """,
                Long.class,
                studentAnswerId,
                scoreVersion,
                rubricId
        );
        return count == null ? 0L : count;
    }

    public Optional<PerformanceRuleSet> findActiveStudentScoreRuleSet(String schoolId, Instant now) {
        List<PerformanceRuleSet> rows = jdbcTemplate.query(
                """
                SELECT performance_rule_set_id, rule_definition
                  FROM performance_rule_sets
                 WHERE metric_scope = 'student_score'
                   AND rule_status = 'active'
                   AND (school_id = ? OR school_id IS NULL)
                   AND (effective_from_at IS NULL OR effective_from_at <= ?)
                   AND (effective_until_at IS NULL OR effective_until_at > ?)
                 ORDER BY CASE WHEN school_id = ? THEN 0 ELSE 1 END,
                          effective_from_at DESC,
                          performance_rule_set_id DESC
                 LIMIT 1
                """,
                (resultSet, rowNumber) -> new PerformanceRuleSet(
                        resultSet.getLong("performance_rule_set_id"),
                        resultSet.getString("rule_definition")
                ),
                schoolId,
                Timestamp.from(now),
                Timestamp.from(now),
                schoolId
        );
        return rows.stream().findFirst();
    }

    public int updateObjectiveScore(
            long studentAnswerId,
            boolean isCorrect,
            BigDecimal pointsEarned
    ) {
        return jdbcTemplate.update(
                """
                UPDATE student_answers
                   SET is_correct = ?,
                       points_earned = ?
                 WHERE student_answer_id = ?
                   AND (NOT (is_correct <=> ?) OR points_earned <> ?)
                """,
                isCorrect,
                pointsEarned,
                studentAnswerId,
                isCorrect,
                pointsEarned
        );
    }

    public int updateResultScore(
            long testResultId,
            int expectedScoreVersion,
            BigDecimal totalScore,
            BigDecimal maxScore,
            int itemsEvaluated,
            BigDecimal percentage,
            String performanceStatus,
            long performanceRuleSetId,
            int newScoreVersion,
            Instant now
    ) {
        Timestamp timestamp = Timestamp.from(now);
        return jdbcTemplate.update(
                """
                UPDATE test_results
                   SET total_score = ?,
                       max_score = ?,
                       items_evaluated = ?,
                       result_status = 'finalized',
                       verification_completed_at = COALESCE(verification_completed_at, ?),
                       scored_at = ?,
                       finalized_at = COALESCE(finalized_at, ?),
                       percentage_snapshot = ?,
                       performance_status = ?,
                       performance_rule_set_id = ?,
                       score_version = ?,
                       checked_at = ?
                 WHERE test_result_id = ?
                   AND score_version = ?
                """,
                totalScore,
                maxScore,
                itemsEvaluated,
                timestamp,
                timestamp,
                timestamp,
                percentage,
                performanceStatus,
                performanceRuleSetId,
                newScoreVersion,
                timestamp,
                testResultId,
                expectedScoreVersion
        );
    }

    private ResultContext mapResultContext(ResultSet resultSet) throws SQLException {
        return new ResultContext(
                resultSet.getLong("test_result_id"),
                resultSet.getString("result_uuid"),
                resultSet.getLong("test_assignment_id"),
                resultSet.getLong("test_id"),
                resultSet.getLong("class_list_id"),
                resultSet.getLong("student_id"),
                resultSet.getString("student_name"),
                resultSet.getInt("attempt_number"),
                resultSet.getBigDecimal("total_score"),
                resultSet.getBigDecimal("max_score"),
                resultSet.getInt("items_evaluated"),
                resultSet.getString("result_status"),
                resultSet.getBigDecimal("percentage_snapshot"),
                resultSet.getString("performance_status"),
                nullableLong(resultSet, "performance_rule_set_id"),
                resultSet.getInt("score_version"),
                nullableInstant(resultSet, "scored_at"),
                resultSet.getLong("teacher_user_id"),
                resultSet.getString("test_school_id"),
                resultSet.getString("student_school_id"),
                resultSet.getLong("assignment_class_id"),
                resultSet.getLong("membership_class_id"),
                resultSet.getString("test_status"),
                resultSet.getString("test_assignment_status")
        );
    }

    private ScoringRow mapScoringRow(ResultSet resultSet) throws SQLException {
        return new ScoringRow(
                resultSet.getLong("test_part_id"),
                resultSet.getInt("part_order"),
                resultSet.getString("part_name"),
                resultSet.getLong("question_id"),
                resultSet.getInt("item_number"),
                resultSet.getString("question_type_code"),
                resultSet.getBigDecimal("maximum_points"),
                nullableLong(resultSet, "question_rubric_id"),
                nullableLong(resultSet, "answer_key_id"),
                resultSet.getString("answer_key_type"),
                nullableLong(resultSet, "correct_question_option_id"),
                nullableLong(resultSet, "answer_key_rubric_id"),
                nullableLong(resultSet, "student_answer_id"),
                resultSet.getString("answer_uuid"),
                nullableLong(resultSet, "selected_question_option_id"),
                nullableLong(resultSet, "selected_option_question_id"),
                resultSet.getString("response_text"),
                resultSet.getLong("written_evidence_attachment_count"),
                resultSet.getString("answer_status"),
                resultSet.getString("evaluation_status"),
                nullableLong(resultSet, "verified_by_user_id"),
                nullableInstant(resultSet, "verified_at"),
                nullableBoolean(resultSet, "is_correct"),
                resultSet.getBigDecimal("points_earned"),
                nullableInstant(resultSet, "finalized_at"),
                resultSet.getInt("answer_score_version")
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Boolean nullableBoolean(ResultSet resultSet, String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    private Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
