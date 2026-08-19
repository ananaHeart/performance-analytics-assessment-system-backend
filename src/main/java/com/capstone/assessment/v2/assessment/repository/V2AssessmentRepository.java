package com.capstone.assessment.v2.assessment.repository;

import com.capstone.assessment.v2.assessment.dto.V2AssessmentAssignmentOption;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentSkillOption;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentSummaryResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentTermPeriodOption;
import com.capstone.assessment.v2.assessment.model.V2AssessmentAssignmentContext;
import com.capstone.assessment.v2.assessment.model.V2AssessmentHeader;
import com.capstone.assessment.v2.assessment.model.V2AssessmentPartRow;
import com.capstone.assessment.v2.assessment.model.V2AssessmentQuestionRow;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Profile("v2")
@Repository
public class V2AssessmentRepository {

    private static final String ASSIGNMENT_CONTEXT_SELECT = """
            SELECT ca.class_assignment_id,
                   ca.class_id,
                   ca.user_id AS teacher_user_id,
                   u.school_id,
                   ca.subject_id,
                   sub.subject_name,
                   ca.status AS assignment_status,
                   ca.assignment_role,
                   c.academic_year_id,
                   ay.year_name,
                   gl.grade_level_id,
                   gl.grade_level_name,
                   s.section_id,
                   s.section_name,
                   c.status AS class_status
              FROM class_assignments ca
              JOIN users u ON u.user_id = ca.user_id
              JOIN subjects sub ON sub.subject_id = ca.subject_id
              JOIN classes c ON c.class_id = ca.class_id
              JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
              JOIN sections s ON s.section_id = c.section_id
              JOIN grade_levels gl ON gl.grade_level_id = s.grade_level_id
            """;

    private static final String HEADER_SELECT = """
            SELECT t.test_id,
                   t.class_assignment_id,
                   ca.class_id,
                   ca.user_id AS teacher_user_id,
                   u.school_id,
                   c.academic_year_id,
                   ay.year_name,
                   gl.grade_level_id,
                   gl.grade_level_name,
                   s.section_id,
                   s.section_name,
                   sub.subject_id,
                   sub.subject_name,
                   tp.term_period_id,
                   tp.term_name,
                   t.test_name,
                   t.test_type,
                   t.test_date,
                   t.instructions,
                   t.total_items,
                   t.status,
                   t.created_at,
                   t.updated_at
              FROM tests t
              JOIN class_assignments ca ON ca.class_assignment_id = t.class_assignment_id
              JOIN users u ON u.user_id = ca.user_id
              JOIN classes c ON c.class_id = ca.class_id
              JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
              JOIN sections s ON s.section_id = c.section_id
              JOIN grade_levels gl ON gl.grade_level_id = s.grade_level_id
              JOIN subjects sub ON sub.subject_id = ca.subject_id
              JOIN term_periods tp ON tp.term_period_id = t.term_period_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public V2AssessmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<V2AssessmentAssignmentOption> listActiveTeacherAssignments(long teacherUserId, String schoolId) {
        return jdbcTemplate.query(
                ASSIGNMENT_CONTEXT_SELECT + """
                 WHERE ca.user_id = ?
                   AND u.school_id = ?
                   AND ca.status = 'active'
                   AND c.status = 'active'
                 ORDER BY ay.start_date DESC, gl.grade_level_id, s.section_name, sub.subject_name
                """,
                (rs, rowNum) -> new V2AssessmentAssignmentOption(
                        rs.getLong("class_assignment_id"),
                        rs.getLong("class_id"),
                        rs.getInt("academic_year_id"),
                        rs.getString("year_name"),
                        rs.getInt("grade_level_id"),
                        rs.getString("grade_level_name"),
                        rs.getInt("section_id"),
                        rs.getString("section_name"),
                        rs.getInt("subject_id"),
                        rs.getString("subject_name"),
                        rs.getString("assignment_role")
                ),
                teacherUserId,
                schoolId
        );
    }

    public Optional<V2AssessmentAssignmentContext> findAssignmentContext(long classAssignmentId) {
        return jdbcTemplate.query(
                ASSIGNMENT_CONTEXT_SELECT + " WHERE ca.class_assignment_id = ?",
                this::mapAssignmentContext,
                classAssignmentId
        ).stream().findFirst();
    }

    public List<V2AssessmentTermPeriodOption> listTermPeriods(int academicYearId) {
        return jdbcTemplate.query(
                """
                SELECT term_period_id, term_name, term_order, status
                  FROM term_periods
                 WHERE academic_year_id = ?
                 ORDER BY term_order
                """,
                (rs, rowNum) -> new V2AssessmentTermPeriodOption(
                        rs.getInt("term_period_id"),
                        rs.getString("term_name"),
                        rs.getInt("term_order"),
                        rs.getString("status")
                ),
                academicYearId
        );
    }

    public boolean termPeriodBelongsToAcademicYear(int termPeriodId, int academicYearId) {
        return exists(
                "SELECT COUNT(*) FROM term_periods WHERE term_period_id = ? AND academic_year_id = ?",
                termPeriodId,
                academicYearId
        );
    }

    public List<V2AssessmentSkillOption> listSkillsForContext(
            int termPeriodId,
            int gradeLevelId,
            int subjectId
    ) {
        return jdbcTemplate.query(
                """
                SELECT sk.skill_id,
                       ct.competency_id,
                       ct.competency_name,
                       rt.root_tag_id,
                       rt.root_tag_name,
                       sk.term_period_id,
                       sk.grade_level_id,
                       sk.subject_id
                  FROM skills sk
                  JOIN competency_tags ct ON ct.competency_id = sk.competency_id
                  JOIN root_tags rt ON rt.root_tag_id = ct.root_tag_id
                 WHERE sk.term_period_id = ?
                   AND sk.grade_level_id = ?
                   AND sk.subject_id = ?
                   AND rt.status = 'active'
                 ORDER BY rt.root_tag_name, ct.competency_name, sk.skill_id
                """,
                (rs, rowNum) -> new V2AssessmentSkillOption(
                        rs.getLong("skill_id"),
                        rs.getLong("competency_id"),
                        rs.getString("competency_name"),
                        rs.getInt("root_tag_id"),
                        rs.getString("root_tag_name"),
                        rs.getInt("term_period_id"),
                        rs.getInt("grade_level_id"),
                        rs.getInt("subject_id")
                ),
                termPeriodId,
                gradeLevelId,
                subjectId
        );
    }

    public Set<Long> findValidSkillIds(
            Set<Long> skillIds,
            int termPeriodId,
            int gradeLevelId,
            int subjectId
    ) {
        if (skillIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", Collections.nCopies(skillIds.size(), "?"));
        String sql = """
                SELECT sk.skill_id
                  FROM skills sk
                  JOIN competency_tags ct ON ct.competency_id = sk.competency_id
                  JOIN root_tags rt ON rt.root_tag_id = ct.root_tag_id
                 WHERE sk.skill_id IN (%s)
                   AND sk.term_period_id = ?
                   AND sk.grade_level_id = ?
                   AND sk.subject_id = ?
                   AND rt.status = 'active'
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(skillIds);
        params.add(termPeriodId);
        params.add(gradeLevelId);
        params.add(subjectId);
        return new LinkedHashSet<>(jdbcTemplate.queryForList(sql, Long.class, params.toArray()));
    }

    public long insertTest(
            long classAssignmentId,
            int termPeriodId,
            String testName,
            String testType,
            LocalDate testDate,
            String instructions,
            int totalItems
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO tests (
                        class_assignment_id,
                        term_period_id,
                        test_name,
                        test_type,
                        test_date,
                        instructions,
                        total_items,
                        status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 'draft')
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, classAssignmentId);
            statement.setInt(2, termPeriodId);
            statement.setString(3, testName);
            statement.setString(4, testType);
            statement.setDate(5, Date.valueOf(testDate));
            statement.setString(6, instructions);
            statement.setInt(7, totalItems);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "Assessment identifier was not generated.");
    }

    public int updateTestHeader(
            long testId,
            int termPeriodId,
            String testName,
            String testType,
            LocalDate testDate,
            String instructions,
            int totalItems
    ) {
        return jdbcTemplate.update(
                """
                UPDATE tests
                   SET term_period_id = ?,
                       test_name = ?,
                       test_type = ?,
                       test_date = ?,
                       instructions = ?,
                       total_items = ?
                 WHERE test_id = ?
                   AND status = 'draft'
                """,
                termPeriodId,
                testName,
                testType,
                Date.valueOf(testDate),
                instructions,
                totalItems,
                testId
        );
    }

    public long insertTestPart(
            long testId,
            int partOrder,
            String partName,
            String partType,
            int numberOfItems,
            BigDecimal pointsPerItem
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO test_parts (
                        test_id,
                        part_order,
                        part_name,
                        part_type,
                        number_of_items,
                        points_per_item
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, testId);
            statement.setInt(2, partOrder);
            statement.setString(3, partName);
            statement.setString(4, partType);
            statement.setInt(5, numberOfItems);
            statement.setBigDecimal(6, pointsPerItem);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "Test part identifier was not generated.");
    }

    public long insertQuestion(
            long testPartId,
            int itemNumber,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String optionE
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO questions (
                        test_part_id,
                        item_number,
                        question_text,
                        option_a,
                        option_b,
                        option_c,
                        option_d,
                        option_e
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, testPartId);
            statement.setInt(2, itemNumber);
            statement.setString(3, questionText);
            statement.setString(4, optionA);
            statement.setString(5, optionB);
            statement.setString(6, optionC);
            statement.setString(7, optionD);
            statement.setString(8, optionE);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "Question identifier was not generated.");
    }

    public void insertAnswerKey(long questionId, String correctOption) {
        jdbcTemplate.update(
                "INSERT INTO answer_keys (question_id, correct_option) VALUES (?, ?)",
                questionId,
                correctOption
        );
    }

    public void insertMapping(long questionId, long skillId) {
        jdbcTemplate.update(
                "INSERT INTO mappings (question_id, skill_id) VALUES (?, ?)",
                questionId,
                skillId
        );
    }

    public void deleteParts(long testId) {
        jdbcTemplate.update("DELETE FROM test_parts WHERE test_id = ?", testId);
    }

    public void lockTest(long testId) {
        jdbcTemplate.queryForObject(
                "SELECT test_id FROM tests WHERE test_id = ? FOR UPDATE",
                Long.class,
                testId
        );
    }

    public Optional<V2AssessmentHeader> findHeader(long testId) {
        return jdbcTemplate.query(
                HEADER_SELECT + " WHERE t.test_id = ?",
                this::mapHeader,
                testId
        ).stream().findFirst();
    }

    public List<V2AssessmentPartRow> findParts(long testId) {
        return jdbcTemplate.query(
                """
                SELECT test_part_id,
                       part_order,
                       part_name,
                       part_type,
                       number_of_items,
                       points_per_item
                  FROM test_parts
                 WHERE test_id = ?
                 ORDER BY part_order
                """,
                (rs, rowNum) -> new V2AssessmentPartRow(
                        rs.getLong("test_part_id"),
                        rs.getInt("part_order"),
                        rs.getString("part_name"),
                        rs.getString("part_type"),
                        rs.getInt("number_of_items"),
                        rs.getBigDecimal("points_per_item")
                ),
                testId
        );
    }

    public List<V2AssessmentQuestionRow> findQuestions(long testId) {
        return jdbcTemplate.query(
                """
                SELECT q.question_id,
                       q.test_part_id,
                       q.item_number,
                       q.question_text,
                       q.option_a,
                       q.option_b,
                       q.option_c,
                       q.option_d,
                       q.option_e,
                       ak.correct_option,
                       GROUP_CONCAT(m.skill_id ORDER BY m.skill_id SEPARATOR ',') AS skill_ids
                  FROM test_parts tp
                  JOIN questions q ON q.test_part_id = tp.test_part_id
                  LEFT JOIN answer_keys ak ON ak.question_id = q.question_id
                  LEFT JOIN mappings m ON m.question_id = q.question_id
                 WHERE tp.test_id = ?
                 GROUP BY q.question_id, q.test_part_id, q.item_number, q.question_text,
                          q.option_a, q.option_b, q.option_c, q.option_d, q.option_e, ak.correct_option
                 ORDER BY tp.part_order, q.item_number
                """,
                (rs, rowNum) -> new V2AssessmentQuestionRow(
                        rs.getLong("question_id"),
                        rs.getLong("test_part_id"),
                        rs.getInt("item_number"),
                        rs.getString("question_text"),
                        rs.getString("option_a"),
                        rs.getString("option_b"),
                        rs.getString("option_c"),
                        rs.getString("option_d"),
                        rs.getString("option_e"),
                        rs.getString("correct_option"),
                        parseSkillIds(rs.getString("skill_ids"))
                ),
                testId
        );
    }

    public List<V2AssessmentSummaryResponse> listAssessments(
            long teacherUserId,
            String schoolId,
            Long classAssignmentId
    ) {
        String classAssignmentFilter = classAssignmentId == null ? "" : " AND t.class_assignment_id = ?";
        String sql = HEADER_SELECT + """
                 WHERE ca.user_id = ?
                   AND u.school_id = ?
                """ + classAssignmentFilter + """
                 ORDER BY t.created_at DESC, t.test_id DESC
                """;
        List<Object> params = new ArrayList<>();
        params.add(teacherUserId);
        params.add(schoolId);
        if (classAssignmentId != null) {
            params.add(classAssignmentId);
        }
        return jdbcTemplate.query(sql, this::mapSummary, params.toArray());
    }

    public int updateStatus(long testId, String status) {
        return jdbcTemplate.update(
                "UPDATE tests SET status = ? WHERE test_id = ?",
                status,
                testId
        );
    }

    public int countParts(long testId) {
        return count("SELECT COUNT(*) FROM test_parts WHERE test_id = ?", testId);
    }

    public int countQuestions(long testId) {
        return count(
                """
                SELECT COUNT(*)
                  FROM questions q
                  JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                 WHERE tp.test_id = ?
                """,
                testId
        );
    }

    public int countQuestionsMissingAnswerKey(long testId) {
        return count(
                """
                SELECT COUNT(*)
                  FROM questions q
                  JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                 WHERE tp.test_id = ?
                   AND NOT EXISTS (
                       SELECT 1 FROM answer_keys ak WHERE ak.question_id = q.question_id
                   )
                """,
                testId
        );
    }

    public int countQuestionsMissingMappings(long testId) {
        return count(
                """
                SELECT COUNT(*)
                  FROM questions q
                  JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                 WHERE tp.test_id = ?
                   AND NOT EXISTS (
                       SELECT 1 FROM mappings m WHERE m.question_id = q.question_id
                   )
                """,
                testId
        );
    }

    public int countInvalidTrueFalseAnswerKeys(long testId) {
        return count(
                """
                SELECT COUNT(*)
                  FROM test_parts tp
                  JOIN questions q ON q.test_part_id = tp.test_part_id
                  JOIN answer_keys ak ON ak.question_id = q.question_id
                 WHERE tp.test_id = ?
                   AND tp.part_type = 'true_false'
                   AND ak.correct_option NOT IN ('A', 'B')
                """,
                testId
        );
    }

    public int countSnapshotMismatches(long testId) {
        Integer partMismatches = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM (
                        SELECT tp.test_part_id
                          FROM test_parts tp
                          LEFT JOIN questions q ON q.test_part_id = tp.test_part_id
                         WHERE tp.test_id = ?
                         GROUP BY tp.test_part_id, tp.number_of_items
                        HAVING COUNT(q.question_id) <> tp.number_of_items
                       ) mismatches
                """,
                Integer.class,
                testId
        );
        Integer totalMismatch = jdbcTemplate.queryForObject(
                """
                SELECT CASE WHEN t.total_items = COUNT(q.question_id) THEN 0 ELSE 1 END
                  FROM tests t
                  LEFT JOIN test_parts tp ON tp.test_id = t.test_id
                  LEFT JOIN questions q ON q.test_part_id = tp.test_part_id
                 WHERE t.test_id = ?
                 GROUP BY t.test_id, t.total_items
                """,
                Integer.class,
                testId
        );
        return (partMismatches == null ? 0 : partMismatches)
                + (totalMismatch == null ? 1 : totalMismatch);
    }

    private V2AssessmentAssignmentContext mapAssignmentContext(ResultSet rs, int rowNum) throws SQLException {
        return new V2AssessmentAssignmentContext(
                rs.getLong("class_assignment_id"),
                rs.getLong("class_id"),
                rs.getLong("teacher_user_id"),
                rs.getString("school_id"),
                rs.getInt("subject_id"),
                rs.getString("subject_name"),
                rs.getString("assignment_status"),
                rs.getString("assignment_role"),
                rs.getInt("academic_year_id"),
                rs.getString("year_name"),
                rs.getInt("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getInt("section_id"),
                rs.getString("section_name"),
                rs.getString("class_status")
        );
    }

    private V2AssessmentHeader mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new V2AssessmentHeader(
                rs.getLong("test_id"),
                rs.getLong("class_assignment_id"),
                rs.getLong("class_id"),
                rs.getLong("teacher_user_id"),
                rs.getString("school_id"),
                rs.getInt("academic_year_id"),
                rs.getString("year_name"),
                rs.getInt("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getInt("section_id"),
                rs.getString("section_name"),
                rs.getInt("subject_id"),
                rs.getString("subject_name"),
                rs.getInt("term_period_id"),
                rs.getString("term_name"),
                rs.getString("test_name"),
                rs.getString("test_type"),
                toLocalDate(rs.getDate("test_date")),
                rs.getString("instructions"),
                rs.getInt("total_items"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private V2AssessmentSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new V2AssessmentSummaryResponse(
                rs.getLong("test_id"),
                rs.getLong("class_assignment_id"),
                rs.getLong("class_id"),
                rs.getInt("academic_year_id"),
                rs.getString("year_name"),
                rs.getInt("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getInt("section_id"),
                rs.getString("section_name"),
                rs.getInt("subject_id"),
                rs.getString("subject_name"),
                rs.getInt("term_period_id"),
                rs.getString("term_name"),
                rs.getString("test_name"),
                rs.getString("test_type"),
                toLocalDate(rs.getDate("test_date")),
                rs.getInt("total_items"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private List<Long> parseSkillIds(String skillIds) {
        if (skillIds == null || skillIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(skillIds.split(","))
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .distinct()
                .toList();
    }

    private long generatedId(KeyHolder keyHolder, String message) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(message);
        }
        return key.longValue();
    }

    private boolean exists(String sql, Object... params) {
        return count(sql, params) > 0;
    }

    private int count(String sql, Object... params) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return count == null ? 0 : count;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
