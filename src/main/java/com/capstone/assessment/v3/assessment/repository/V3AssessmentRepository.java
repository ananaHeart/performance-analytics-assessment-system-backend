package com.capstone.assessment.v3.assessment.repository;

import com.capstone.assessment.v3.assessment.dto.V3AssessmentReferenceDataResponse;
import com.capstone.assessment.v3.assessment.dto.V3AssessmentSummaryResponse;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.AcceptedAnswerRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.AnswerKeyRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.AssessmentHeader;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.AssignmentContext;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.ClassScheduleWindow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.OptionRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.PartRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.QuestionRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.QuestionType;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.RubricCriterionRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.RubricRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.SkillMappingRow;
import com.capstone.assessment.v3.assessment.model.V3AssessmentModels.TermWindow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Profile("v3")
@Repository
public class V3AssessmentRepository {

    private static final String ASSIGNMENT_CONTEXT_SELECT = """
            SELECT ca.class_assignment_id,
                   ca.user_id AS teacher_user_id,
                   u.school_id,
                   ca.class_id,
                   c.academic_year_id,
                   ay.year_name AS academic_year_name,
                   sec.grade_level_id,
                   gl.grade_level_name,
                   sec.section_name,
                   ca.subject_id,
                   sub.subject_name,
                   ca.assignment_role,
                   ca.status AS assignment_status,
                   c.status AS class_status
            FROM class_assignments ca
            JOIN users u ON u.user_id = ca.user_id
            JOIN classes c ON c.class_id = ca.class_id
            JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
            JOIN sections sec ON sec.section_id = c.section_id
            JOIN grade_levels gl ON gl.grade_level_id = sec.grade_level_id
            JOIN subjects sub ON sub.subject_id = ca.subject_id
            """;

    private static final String HEADER_SELECT = """
            SELECT t.test_id,
                   t.test_uuid,
                   t.school_id,
                   t.created_by_user_id,
                   t.version_number,
                   t.term_period_id,
                   tp.term_name,
                   t.test_name,
                   t.test_type,
                   t.instructions,
                   t.total_items,
                   t.status,
                   ta.test_assignment_id,
                   ta.assignment_uuid,
                   ta.class_assignment_id,
                   ta.open_at,
                   ta.close_at,
                   ta.assignment_status,
                   ta.allow_late_capture,
                   ta.outside_schedule_confirmed,
                   ta.outside_schedule_reason,
                   ta.outside_schedule_confirmed_by_user_id,
                   ta.outside_schedule_confirmed_at,
                   ca.class_id,
                   c.academic_year_id,
                   ay.year_name AS academic_year_name,
                   sec.grade_level_id,
                   gl.grade_level_name,
                   sec.section_name,
                   ca.subject_id,
                   sub.subject_name,
                   t.created_at,
                   t.updated_at
            FROM tests t
            JOIN term_periods tp ON tp.term_period_id = t.term_period_id
            JOIN test_assignments ta ON ta.test_id = t.test_id
            JOIN class_assignments ca ON ca.class_assignment_id = ta.class_assignment_id
            JOIN classes c ON c.class_id = ca.class_id
            JOIN academic_years ay ON ay.academic_year_id = c.academic_year_id
            JOIN sections sec ON sec.section_id = c.section_id
            JOIN grade_levels gl ON gl.grade_level_id = sec.grade_level_id
            JOIN subjects sub ON sub.subject_id = ca.subject_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public V3AssessmentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<AssignmentContext> listActiveAssignments(long teacherUserId, String schoolId) {
        return jdbcTemplate.query(
                ASSIGNMENT_CONTEXT_SELECT + """
                        WHERE ca.user_id = ?
                          AND u.school_id = ?
                          AND ca.status = 'active'
                          AND c.status = 'active'
                        ORDER BY ay.start_date DESC,
                                 gl.grade_level_name,
                                 sec.section_name,
                                 sub.subject_name
                        """,
                this::mapAssignmentContext,
                teacherUserId,
                schoolId
        );
    }

    public Optional<AssignmentContext> findAssignmentContext(long classAssignmentId) {
        return jdbcTemplate.query(
                ASSIGNMENT_CONTEXT_SELECT + " WHERE ca.class_assignment_id = ?",
                this::mapAssignmentContext,
                classAssignmentId
        ).stream().findFirst();
    }

    public List<V3AssessmentReferenceDataResponse.TermPeriodOption> listTermPeriods(int academicYearId) {
        return jdbcTemplate.query("""
                        SELECT term_period_id, term_name, term_order, start_at, end_at, status
                        FROM term_periods
                        WHERE academic_year_id = ?
                        ORDER BY term_order
                        """,
                (rs, rowNum) -> new V3AssessmentReferenceDataResponse.TermPeriodOption(
                        rs.getInt("term_period_id"),
                        rs.getString("term_name"),
                        rs.getInt("term_order"),
                        toInstant(rs.getTimestamp("start_at")),
                        toInstant(rs.getTimestamp("end_at")),
                        rs.getString("status")
                ),
                academicYearId
        );
    }

    public boolean termPeriodBelongsToAcademicYear(int termPeriodId, int academicYearId) {
        return exists("""
                SELECT 1
                FROM term_periods
                WHERE term_period_id = ? AND academic_year_id = ?
                """, termPeriodId, academicYearId);
    }

    public Optional<TermWindow> findTermWindow(
            int termPeriodId,
            int academicYearId,
            String schoolId
    ) {
        return jdbcTemplate.query("""
                        SELECT term.term_period_id,
                               term.academic_year_id,
                               term.start_at,
                               term.end_at,
                               term.status
                          FROM term_periods term
                          JOIN academic_years academic_year
                            ON academic_year.academic_year_id = term.academic_year_id
                         WHERE term.term_period_id = ?
                           AND term.academic_year_id = ?
                           AND academic_year.school_id = ?
                        """,
                (rs, rowNum) -> new TermWindow(
                        rs.getInt("term_period_id"),
                        rs.getInt("academic_year_id"),
                        toInstant(rs.getTimestamp("start_at")),
                        toInstant(rs.getTimestamp("end_at")),
                        rs.getString("status")
                ),
                termPeriodId,
                academicYearId,
                schoolId
        ).stream().findFirst();
    }

    public List<ClassScheduleWindow> listActiveClassScheduleWindows(long classAssignmentId) {
        return jdbcTemplate.query("""
                        SELECT day_of_week,
                               start_time,
                               end_time,
                               timezone_name,
                               effective_from,
                               effective_to
                          FROM class_assignment_schedules
                         WHERE class_assignment_id = ?
                           AND schedule_status = 'active'
                         ORDER BY day_of_week, start_time, effective_from
                        """,
                (rs, rowNum) -> {
                    java.sql.Date effectiveTo = rs.getDate("effective_to");
                    return new ClassScheduleWindow(
                            rs.getInt("day_of_week"),
                            rs.getTime("start_time").toLocalTime(),
                            rs.getTime("end_time").toLocalTime(),
                            rs.getString("timezone_name"),
                            rs.getDate("effective_from").toLocalDate(),
                            effectiveTo == null ? null : effectiveTo.toLocalDate()
                    );
                },
                classAssignmentId
        );
    }

    public List<QuestionType> listActiveQuestionTypes() {
        return jdbcTemplate.query("""
                        SELECT question_type_id,
                               question_type_code,
                               question_type_name,
                               capture_mode,
                               scoring_mode,
                               supports_omr,
                               supports_ocr,
                               supports_multiple_response,
                               requires_attachment,
                               requires_teacher_verification,
                               allows_teacher_answer_edit,
                               is_active
                        FROM question_types
                        WHERE is_active = 1
                        ORDER BY question_type_id
                        """,
                this::mapQuestionType
        );
    }

    public Optional<QuestionType> findActiveQuestionType(String code) {
        return jdbcTemplate.query("""
                        SELECT question_type_id,
                               question_type_code,
                               question_type_name,
                               capture_mode,
                               scoring_mode,
                               supports_omr,
                               supports_ocr,
                               supports_multiple_response,
                               requires_attachment,
                               requires_teacher_verification,
                               allows_teacher_answer_edit,
                               is_active
                        FROM question_types
                        WHERE question_type_code = ? AND is_active = 1
                        """,
                this::mapQuestionType,
                code
        ).stream().findFirst();
    }

    public List<V3AssessmentReferenceDataResponse.SkillOption> listSkills(
            int termPeriodId,
            int gradeLevelId,
            int subjectId
    ) {
        return jdbcTemplate.query("""
                        SELECT sk.skill_id,
                               sk.competency_id,
                               rt.root_tag_id,
                               rt.root_tag_name,
                               ct.competency_name,
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
                        ORDER BY rt.root_tag_name, ct.competency_name
                        """,
                (rs, rowNum) -> new V3AssessmentReferenceDataResponse.SkillOption(
                        rs.getLong("skill_id"),
                        rs.getLong("competency_id"),
                        rs.getInt("root_tag_id"),
                        rs.getString("root_tag_name"),
                        rs.getString("competency_name"),
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
        String placeholders = String.join(",", java.util.Collections.nCopies(skillIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(termPeriodId);
        parameters.add(gradeLevelId);
        parameters.add(subjectId);
        parameters.addAll(skillIds);
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                        SELECT sk.skill_id
                        FROM skills sk
                        JOIN competency_tags ct ON ct.competency_id = sk.competency_id
                        JOIN root_tags rt ON rt.root_tag_id = ct.root_tag_id
                        WHERE sk.term_period_id = ?
                          AND sk.grade_level_id = ?
                          AND sk.subject_id = ?
                          AND rt.status = 'active'
                          AND sk.skill_id IN (""" + placeholders + ")",
                Long.class,
                parameters.toArray()
        ));
    }

    public List<V3AssessmentReferenceDataResponse.RubricOption> listRubrics(
            String schoolId,
            long teacherUserId
    ) {
        return jdbcTemplate.query("""
                        SELECT rubric_id, rubric_name, total_points, rubric_status, created_by_user_id
                        FROM rubrics
                        WHERE school_id = ?
                          AND (rubric_status = 'active'
                               OR (rubric_status = 'draft' AND created_by_user_id = ?))
                        ORDER BY rubric_status, rubric_name
                        """,
                (rs, rowNum) -> new V3AssessmentReferenceDataResponse.RubricOption(
                        rs.getLong("rubric_id"),
                        rs.getString("rubric_name"),
                        rs.getBigDecimal("total_points"),
                        rs.getString("rubric_status"),
                        rs.getLong("created_by_user_id")
                ),
                schoolId,
                teacherUserId
        );
    }

    public Optional<RubricRow> findUsableRubric(long rubricId, String schoolId, long teacherUserId) {
        return jdbcTemplate.query("""
                        SELECT rubric_id,
                               rubric_uuid,
                               school_id,
                               created_by_user_id,
                               rubric_name,
                               description,
                               total_points,
                               rubric_status
                        FROM rubrics
                        WHERE rubric_id = ?
                          AND school_id = ?
                          AND (rubric_status = 'active'
                               OR (rubric_status = 'draft' AND created_by_user_id = ?))
                        """,
                this::mapRubric,
                rubricId,
                schoolId,
                teacherUserId
        ).stream().findFirst();
    }

    public List<RubricCriterionRow> findRubricCriteria(long rubricId) {
        return jdbcTemplate.query("""
                        SELECT rubric_criterion_id,
                               rubric_id,
                               criterion_order,
                               criterion_name,
                               criterion_description,
                               maximum_points,
                               level_definition,
                               is_required
                        FROM rubric_criteria
                        WHERE rubric_id = ?
                        ORDER BY criterion_order
                        """,
                this::mapRubricCriterion,
                rubricId
        );
    }

    public long insertTest(
            String testUuid,
            String schoolId,
            long teacherUserId,
            int termPeriodId,
            String testName,
            String testType,
            String instructions,
            int totalItems
    ) {
        return insertAndReturnId("""
                INSERT INTO tests (
                    test_uuid,
                    school_id,
                    created_by_user_id,
                    term_period_id,
                    test_name,
                    test_type,
                    instructions,
                    total_items,
                    status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'draft')
                """,
                testUuid,
                schoolId,
                teacherUserId,
                termPeriodId,
                testName,
                testType,
                instructions,
                totalItems
        );
    }

    public long insertTestAssignment(
            String assignmentUuid,
            long testId,
            long classAssignmentId,
            long teacherUserId,
            Instant openAt,
            Instant closeAt,
            boolean allowLateCapture,
            boolean outsideScheduleConfirmed,
            String outsideScheduleReason,
            Long outsideScheduleConfirmedByUserId,
            Instant outsideScheduleConfirmedAt
    ) {
        return insertAndReturnId("""
                INSERT INTO test_assignments (
                    assignment_uuid,
                    test_id,
                    class_assignment_id,
                    assigned_by_user_id,
                    open_at,
                    close_at,
                    assignment_status,
                    allow_late_capture,
                    outside_schedule_confirmed,
                    outside_schedule_reason,
                    outside_schedule_confirmed_by_user_id,
                    outside_schedule_confirmed_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'planned', ?, ?, ?, ?, ?)
                """,
                assignmentUuid,
                testId,
                classAssignmentId,
                teacherUserId,
                toTimestamp(openAt),
                toTimestamp(closeAt),
                allowLateCapture,
                outsideScheduleConfirmed,
                outsideScheduleReason,
                outsideScheduleConfirmedByUserId,
                toTimestamp(outsideScheduleConfirmedAt)
        );
    }

    public long insertTestPart(
            long testId,
            int partOrder,
            String partName,
            int questionTypeId,
            int numberOfItems,
            BigDecimal pointsPerItem,
            String partInstructions
    ) {
        return insertAndReturnId("""
                INSERT INTO test_parts (
                    test_id,
                    part_order,
                    part_name,
                    question_type_id,
                    number_of_items,
                    points_per_item,
                    part_instructions
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                testId,
                partOrder,
                partName,
                questionTypeId,
                numberOfItems,
                pointsPerItem,
                partInstructions
        );
    }

    public long insertRubric(
            String rubricUuid,
            String schoolId,
            long teacherUserId,
            String rubricName,
            String description,
            BigDecimal totalPoints
    ) {
        return insertAndReturnId("""
                INSERT INTO rubrics (
                    rubric_uuid,
                    school_id,
                    created_by_user_id,
                    rubric_name,
                    description,
                    total_points,
                    rubric_status
                ) VALUES (?, ?, ?, ?, ?, ?, 'draft')
                """,
                rubricUuid,
                schoolId,
                teacherUserId,
                rubricName,
                description,
                totalPoints
        );
    }

    public long insertRubricCriterion(
            long rubricId,
            int criterionOrder,
            String criterionName,
            String criterionDescription,
            BigDecimal maximumPoints,
            String levelDefinition,
            boolean required
    ) {
        return insertAndReturnId("""
                INSERT INTO rubric_criteria (
                    rubric_id,
                    criterion_order,
                    criterion_name,
                    criterion_description,
                    maximum_points,
                    level_definition,
                    is_required
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                rubricId,
                criterionOrder,
                criterionName,
                criterionDescription,
                maximumPoints,
                levelDefinition,
                required
        );
    }

    public long insertQuestion(
            String questionUuid,
            long testPartId,
            int questionTypeId,
            int itemNumber,
            String questionText,
            BigDecimal maximumPoints,
            Long rubricId,
            String responseInstructions,
            boolean answerOrderRequired,
            Integer maximumResponseLength,
            Integer expectedResponseCount,
            String responseRegionSize,
            boolean forcePageBreakBefore
    ) {
        return insertAndReturnId("""
                INSERT INTO questions (
                    question_uuid,
                    test_part_id,
                    question_type_id,
                    item_number,
                    question_text,
                    maximum_points,
                    rubric_id,
                    response_instructions,
                    answer_order_required,
                    maximum_response_length,
                    expected_response_count,
                    response_region_size,
                    force_page_break_before
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                questionUuid,
                testPartId,
                questionTypeId,
                itemNumber,
                questionText,
                maximumPoints,
                rubricId,
                responseInstructions,
                answerOrderRequired,
                maximumResponseLength,
                expectedResponseCount,
                responseRegionSize,
                forcePageBreakBefore
        );
    }

    public long insertQuestionOption(
            long questionId,
            String optionKey,
            String optionText,
            int optionOrder
    ) {
        return insertAndReturnId("""
                INSERT INTO question_options (
                    question_id,
                    option_key,
                    option_text,
                    option_order,
                    is_active
                ) VALUES (?, ?, ?, ?, 1)
                """,
                questionId,
                optionKey,
                optionText,
                optionOrder
        );
    }

    public void insertAnswerKey(
            long questionId,
            String answerKeyType,
            Long correctQuestionOptionId,
            String scoringMethod,
            Long rubricId,
            String answerExplanation
    ) {
        jdbcTemplate.update("""
                        INSERT INTO answer_keys (
                            question_id,
                            answer_key_type,
                            correct_question_option_id,
                            scoring_method,
                            rubric_id,
                            answer_explanation
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                questionId,
                answerKeyType,
                correctQuestionOptionId,
                scoringMethod,
                rubricId,
                answerExplanation
        );
    }

    public long insertAcceptedAnswer(
            long questionId,
            Integer answerOrder,
            String acceptedText,
            String normalizedText,
            String matchingMode,
            boolean caseSensitive,
            BigDecimal points,
            boolean primary
    ) {
        return insertAndReturnId("""
                INSERT INTO accepted_answers (
                    question_id,
                    answer_order,
                    accepted_text,
                    normalized_text,
                    matching_mode,
                    is_case_sensitive,
                    points,
                    is_primary,
                    is_active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """,
                questionId,
                answerOrder,
                acceptedText,
                normalizedText,
                matchingMode,
                caseSensitive,
                points,
                primary
        );
    }

    public long insertPartSkillMapping(
            long testPartId,
            long skillId,
            int startItemNumber,
            int endItemNumber
    ) {
        return insertAndReturnId("""
                INSERT INTO part_skill_mappings (
                    test_part_id,
                    skill_id,
                    start_item_number,
                    end_item_number,
                    item_count
                ) VALUES (?, ?, ?, ?, ?)
                """,
                testPartId,
                skillId,
                startItemNumber,
                endItemNumber,
                endItemNumber - startItemNumber + 1
        );
    }

    public int updateTestHeader(
            long testId,
            int termPeriodId,
            String testName,
            String testType,
            String instructions,
            int totalItems
    ) {
        return jdbcTemplate.update("""
                        UPDATE tests
                        SET term_period_id = ?,
                            test_name = ?,
                            test_type = ?,
                            instructions = ?,
                            total_items = ?
                        WHERE test_id = ? AND status = 'draft'
                        """,
                termPeriodId,
                testName,
                testType,
                instructions,
                totalItems,
                testId
        );
    }

    public boolean isDraftTest(long testId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM tests
                            WHERE test_id = ? AND status = 'draft'
                        )
                        """,
                Boolean.class,
                testId
        );
        return Boolean.TRUE.equals(exists);
    }

    public int updateTestAssignment(
            long testAssignmentId,
            Instant openAt,
            Instant closeAt,
            boolean allowLateCapture,
            boolean outsideScheduleConfirmed,
            String outsideScheduleReason,
            Long outsideScheduleConfirmedByUserId,
            Instant outsideScheduleConfirmedAt
    ) {
        return jdbcTemplate.update("""
                        UPDATE test_assignments
                        SET open_at = ?,
                            close_at = ?,
                            allow_late_capture = ?,
                            outside_schedule_confirmed = ?,
                            outside_schedule_reason = ?,
                            outside_schedule_confirmed_by_user_id = ?,
                            outside_schedule_confirmed_at = ?
                        WHERE test_assignment_id = ? AND assignment_status = 'planned'
                        """,
                toTimestamp(openAt),
                toTimestamp(closeAt),
                allowLateCapture,
                outsideScheduleConfirmed,
                outsideScheduleReason,
                outsideScheduleConfirmedByUserId,
                toTimestamp(outsideScheduleConfirmedAt),
                testAssignmentId
        );
    }

    public boolean isPlannedTestAssignment(long testAssignmentId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM test_assignments
                            WHERE test_assignment_id = ? AND assignment_status = 'planned'
                        )
                        """,
                Boolean.class,
                testAssignmentId
        );
        return Boolean.TRUE.equals(exists);
    }

    public List<Long> findDraftRubricIdsForTest(long testId, long teacherUserId) {
        return jdbcTemplate.queryForList("""
                        SELECT DISTINCT r.rubric_id
                        FROM rubrics r
                        JOIN questions q ON q.rubric_id = r.rubric_id
                        JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                        WHERE tp.test_id = ?
                          AND r.created_by_user_id = ?
                          AND r.rubric_status = 'draft'
                        """,
                Long.class,
                testId,
                teacherUserId
        );
    }

    public void deleteParts(long testId) {
        jdbcTemplate.update("DELETE FROM test_parts WHERE test_id = ?", testId);
    }

    public void deleteDraftRubricIfUnused(long rubricId, long teacherUserId) {
        jdbcTemplate.update("""
                DELETE FROM rubrics
                WHERE rubric_id = ?
                  AND created_by_user_id = ?
                  AND rubric_status = 'draft'
                  AND NOT EXISTS (
                      SELECT 1 FROM questions WHERE questions.rubric_id = rubrics.rubric_id
                  )
                """, rubricId, teacherUserId);
    }

    public void lockTest(long testId) {
        jdbcTemplate.queryForObject(
                "SELECT test_id FROM tests WHERE test_id = ? FOR UPDATE",
                Long.class,
                testId
        );
    }

    public Optional<AssessmentHeader> findHeader(long testId) {
        return jdbcTemplate.query(
                HEADER_SELECT + " WHERE t.test_id = ?",
                this::mapHeader,
                testId
        ).stream().findFirst();
    }

    public List<PartRow> findParts(long testId) {
        return jdbcTemplate.query("""
                        SELECT tp.test_part_id,
                               tp.test_id,
                               tp.part_order,
                               tp.part_name,
                               tp.question_type_id,
                               qt.question_type_code,
                               qt.question_type_name,
                               tp.number_of_items,
                               tp.points_per_item,
                               tp.part_instructions
                        FROM test_parts tp
                        JOIN question_types qt ON qt.question_type_id = tp.question_type_id
                        WHERE tp.test_id = ?
                        ORDER BY tp.part_order
                        """,
                this::mapPart,
                testId
        );
    }

    public List<QuestionRow> findQuestions(long testId) {
        return jdbcTemplate.query("""
                        SELECT q.question_id,
                               q.question_uuid,
                               q.test_part_id,
                               q.item_number,
                               q.question_text,
                               q.maximum_points,
                               q.rubric_id,
                               q.response_instructions,
                               q.answer_order_required,
                               q.maximum_response_length,
                               q.expected_response_count,
                               q.response_region_size,
                               q.force_page_break_before
                        FROM questions q
                        JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                        WHERE tp.test_id = ?
                        ORDER BY tp.part_order, q.item_number
                        """,
                this::mapQuestion,
                testId
        );
    }

    public List<OptionRow> findOptions(long testId) {
        return jdbcTemplate.query("""
                        SELECT qo.question_option_id,
                               qo.question_id,
                               qo.option_key,
                               qo.option_text,
                               qo.option_order
                        FROM question_options qo
                        JOIN questions q ON q.question_id = qo.question_id
                        JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                        WHERE tp.test_id = ? AND qo.is_active = 1
                        ORDER BY tp.part_order, q.item_number, qo.option_order
                        """,
                (rs, rowNum) -> new OptionRow(
                        rs.getLong("question_option_id"),
                        rs.getLong("question_id"),
                        rs.getString("option_key"),
                        rs.getString("option_text"),
                        rs.getInt("option_order")
                ),
                testId
        );
    }

    public List<AnswerKeyRow> findAnswerKeys(long testId) {
        return jdbcTemplate.query("""
                        SELECT ak.question_id,
                               ak.answer_key_type,
                               ak.correct_question_option_id,
                               qo.option_key AS correct_option_key,
                               ak.scoring_method,
                               ak.rubric_id,
                               ak.answer_explanation
                        FROM answer_keys ak
                        JOIN questions q ON q.question_id = ak.question_id
                        JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                        LEFT JOIN question_options qo
                          ON qo.question_option_id = ak.correct_question_option_id
                        WHERE tp.test_id = ?
                        """,
                (rs, rowNum) -> new AnswerKeyRow(
                        rs.getLong("question_id"),
                        rs.getString("answer_key_type"),
                        nullableLong(rs, "correct_question_option_id"),
                        rs.getString("correct_option_key"),
                        rs.getString("scoring_method"),
                        nullableLong(rs, "rubric_id"),
                        rs.getString("answer_explanation")
                ),
                testId
        );
    }

    public List<AcceptedAnswerRow> findAcceptedAnswers(long testId) {
        return jdbcTemplate.query("""
                        SELECT aa.accepted_answer_id,
                               aa.question_id,
                               aa.answer_order,
                               aa.accepted_text,
                               aa.normalized_text,
                               aa.matching_mode,
                               aa.is_case_sensitive,
                               aa.points,
                               aa.is_primary
                        FROM accepted_answers aa
                        JOIN questions q ON q.question_id = aa.question_id
                        JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                        WHERE tp.test_id = ? AND aa.is_active = 1
                        ORDER BY tp.part_order,
                                 q.item_number,
                                 aa.answer_order,
                                 aa.is_primary DESC,
                                 aa.accepted_answer_id
                        """,
                (rs, rowNum) -> new AcceptedAnswerRow(
                        rs.getLong("accepted_answer_id"),
                        rs.getLong("question_id"),
                        nullableInteger(rs, "answer_order"),
                        rs.getString("accepted_text"),
                        rs.getString("normalized_text"),
                        rs.getString("matching_mode"),
                        rs.getBoolean("is_case_sensitive"),
                        rs.getBigDecimal("points"),
                        rs.getBoolean("is_primary")
                ),
                testId
        );
    }

    public List<SkillMappingRow> findSkillMappings(long testId) {
        return jdbcTemplate.query("""
                        SELECT psm.part_skill_mapping_id,
                               psm.test_part_id,
                               psm.skill_id,
                               rt.root_tag_name,
                               ct.competency_name,
                               psm.start_item_number,
                               psm.end_item_number,
                               psm.item_count
                        FROM part_skill_mappings psm
                        JOIN test_parts tp ON tp.test_part_id = psm.test_part_id
                        JOIN skills sk ON sk.skill_id = psm.skill_id
                        JOIN competency_tags ct ON ct.competency_id = sk.competency_id
                        JOIN root_tags rt ON rt.root_tag_id = ct.root_tag_id
                        WHERE tp.test_id = ?
                        ORDER BY tp.part_order,
                                 psm.start_item_number,
                                 psm.end_item_number,
                                 rt.root_tag_name,
                                 ct.competency_name
                        """,
                (rs, rowNum) -> new SkillMappingRow(
                        rs.getLong("part_skill_mapping_id"),
                        rs.getLong("test_part_id"),
                        rs.getLong("skill_id"),
                        rs.getString("root_tag_name"),
                        rs.getString("competency_name"),
                        rs.getInt("start_item_number"),
                        rs.getInt("end_item_number"),
                        rs.getInt("item_count")
                ),
                testId
        );
    }

    public List<RubricRow> findRubricsForTest(long testId) {
        return jdbcTemplate.query("""
                        SELECT DISTINCT r.rubric_id,
                               r.rubric_uuid,
                               r.school_id,
                               r.created_by_user_id,
                               r.rubric_name,
                               r.description,
                               r.total_points,
                               r.rubric_status
                        FROM rubrics r
                        JOIN questions q ON q.rubric_id = r.rubric_id
                        JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                        WHERE tp.test_id = ?
                        ORDER BY r.rubric_name
                        """,
                this::mapRubric,
                testId
        );
    }

    public List<RubricCriterionRow> findRubricCriteriaForTest(long testId) {
        return jdbcTemplate.query("""
                        SELECT DISTINCT rc.rubric_criterion_id,
                               rc.rubric_id,
                               rc.criterion_order,
                               rc.criterion_name,
                               rc.criterion_description,
                               rc.maximum_points,
                               rc.level_definition,
                               rc.is_required
                        FROM rubric_criteria rc
                        JOIN questions q ON q.rubric_id = rc.rubric_id
                        JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                        WHERE tp.test_id = ?
                        ORDER BY rc.rubric_id, rc.criterion_order
                        """,
                this::mapRubricCriterion,
                testId
        );
    }

    public List<V3AssessmentSummaryResponse> listAssessments(
            long teacherUserId,
            String schoolId,
            long classAssignmentId
    ) {
        return jdbcTemplate.query("""
                        SELECT t.test_id,
                               t.test_uuid,
                               ta.test_assignment_id,
                               ta.class_assignment_id,
                               t.test_name,
                               t.test_type,
                               tp.term_name,
                               t.total_items,
                               t.status,
                               ta.assignment_status,
                               ta.open_at,
                               ta.close_at,
                               t.updated_at
                        FROM tests t
                        JOIN term_periods tp ON tp.term_period_id = t.term_period_id
                        JOIN test_assignments ta ON ta.test_id = t.test_id
                        JOIN class_assignments ca
                          ON ca.class_assignment_id = ta.class_assignment_id
                        WHERE t.created_by_user_id = ?
                          AND t.school_id = ?
                          AND ca.user_id = ?
                          AND ta.class_assignment_id = ?
                        ORDER BY t.updated_at DESC, t.test_id DESC
                        """,
                (rs, rowNum) -> new V3AssessmentSummaryResponse(
                        rs.getLong("test_id"),
                        rs.getString("test_uuid"),
                        rs.getLong("test_assignment_id"),
                        rs.getLong("class_assignment_id"),
                        rs.getString("test_name"),
                        rs.getString("test_type"),
                        rs.getString("term_name"),
                        rs.getInt("total_items"),
                        rs.getString("status"),
                        rs.getString("assignment_status"),
                        toInstant(rs.getTimestamp("open_at")),
                        toInstant(rs.getTimestamp("close_at")),
                        toInstant(rs.getTimestamp("updated_at"))
                ),
                teacherUserId,
                schoolId,
                teacherUserId,
                classAssignmentId
        );
    }

    public int activateTest(long testId, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE tests
                        SET status = 'active',
                            published_at = ?,
                            content_locked_at = ?
                        WHERE test_id = ? AND status = 'draft'
                        """,
                Timestamp.from(now),
                Timestamp.from(now),
                testId
        );
    }

    public int activateTestAssignment(long testAssignmentId, String assignmentStatus) {
        return jdbcTemplate.update("""
                        UPDATE test_assignments
                        SET assignment_status = ?
                        WHERE test_assignment_id = ? AND assignment_status = 'planned'
                        """,
                assignmentStatus,
                testAssignmentId
        );
    }

    public void activateDraftRubricsForTest(long testId, long teacherUserId) {
        jdbcTemplate.update("""
                UPDATE rubrics r
                JOIN (
                    SELECT DISTINCT q.rubric_id
                    FROM questions q
                    JOIN test_parts tp ON tp.test_part_id = q.test_part_id
                    WHERE tp.test_id = ? AND q.rubric_id IS NOT NULL
                ) used ON used.rubric_id = r.rubric_id
                SET r.rubric_status = 'active'
                WHERE r.created_by_user_id = ? AND r.rubric_status = 'draft'
                """, testId, teacherUserId);
    }

    public int archiveTest(long testId, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE tests
                        SET status = 'archived', archived_at = ?
                        WHERE test_id = ? AND status <> 'archived'
                        """,
                Timestamp.from(now),
                testId
        );
    }

    public void archiveTestAssignment(long testAssignmentId) {
        jdbcTemplate.update("""
                UPDATE test_assignments
                SET assignment_status = 'archived'
                WHERE test_assignment_id = ? AND assignment_status <> 'archived'
                """, testAssignmentId);
    }

    private AssignmentContext mapAssignmentContext(ResultSet rs, int rowNum) throws SQLException {
        return new AssignmentContext(
                rs.getLong("class_assignment_id"),
                rs.getLong("teacher_user_id"),
                rs.getString("school_id"),
                rs.getLong("class_id"),
                rs.getInt("academic_year_id"),
                rs.getString("academic_year_name"),
                rs.getInt("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getString("section_name"),
                rs.getInt("subject_id"),
                rs.getString("subject_name"),
                rs.getString("assignment_role"),
                rs.getString("assignment_status"),
                rs.getString("class_status")
        );
    }

    private QuestionType mapQuestionType(ResultSet rs, int rowNum) throws SQLException {
        return new QuestionType(
                rs.getInt("question_type_id"),
                rs.getString("question_type_code"),
                rs.getString("question_type_name"),
                rs.getString("capture_mode"),
                rs.getString("scoring_mode"),
                rs.getBoolean("supports_omr"),
                rs.getBoolean("supports_ocr"),
                rs.getBoolean("supports_multiple_response"),
                rs.getBoolean("requires_attachment"),
                rs.getBoolean("requires_teacher_verification"),
                rs.getBoolean("allows_teacher_answer_edit"),
                rs.getBoolean("is_active")
        );
    }

    private AssessmentHeader mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new AssessmentHeader(
                rs.getLong("test_id"),
                rs.getString("test_uuid"),
                rs.getString("school_id"),
                rs.getLong("created_by_user_id"),
                rs.getInt("version_number"),
                rs.getInt("term_period_id"),
                rs.getString("term_name"),
                rs.getString("test_name"),
                rs.getString("test_type"),
                rs.getString("instructions"),
                rs.getInt("total_items"),
                rs.getString("status"),
                rs.getLong("test_assignment_id"),
                rs.getString("assignment_uuid"),
                rs.getLong("class_assignment_id"),
                toInstant(rs.getTimestamp("open_at")),
                toInstant(rs.getTimestamp("close_at")),
                rs.getString("assignment_status"),
                rs.getBoolean("allow_late_capture"),
                rs.getBoolean("outside_schedule_confirmed"),
                rs.getString("outside_schedule_reason"),
                rs.getObject("outside_schedule_confirmed_by_user_id", Long.class),
                toInstant(rs.getTimestamp("outside_schedule_confirmed_at")),
                rs.getLong("class_id"),
                rs.getInt("academic_year_id"),
                rs.getString("academic_year_name"),
                rs.getInt("grade_level_id"),
                rs.getString("grade_level_name"),
                rs.getString("section_name"),
                rs.getInt("subject_id"),
                rs.getString("subject_name"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private PartRow mapPart(ResultSet rs, int rowNum) throws SQLException {
        return new PartRow(
                rs.getLong("test_part_id"),
                rs.getLong("test_id"),
                rs.getInt("part_order"),
                rs.getString("part_name"),
                rs.getInt("question_type_id"),
                rs.getString("question_type_code"),
                rs.getString("question_type_name"),
                rs.getInt("number_of_items"),
                rs.getBigDecimal("points_per_item"),
                rs.getString("part_instructions")
        );
    }

    private QuestionRow mapQuestion(ResultSet rs, int rowNum) throws SQLException {
        return new QuestionRow(
                rs.getLong("question_id"),
                rs.getString("question_uuid"),
                rs.getLong("test_part_id"),
                rs.getInt("item_number"),
                rs.getString("question_text"),
                rs.getBigDecimal("maximum_points"),
                nullableLong(rs, "rubric_id"),
                rs.getString("response_instructions"),
                rs.getBoolean("answer_order_required"),
                nullableInteger(rs, "maximum_response_length"),
                nullableInteger(rs, "expected_response_count"),
                rs.getString("response_region_size"),
                rs.getBoolean("force_page_break_before")
        );
    }

    private RubricRow mapRubric(ResultSet rs, int rowNum) throws SQLException {
        return new RubricRow(
                rs.getLong("rubric_id"),
                rs.getString("rubric_uuid"),
                rs.getString("school_id"),
                rs.getLong("created_by_user_id"),
                rs.getString("rubric_name"),
                rs.getString("description"),
                rs.getBigDecimal("total_points"),
                rs.getString("rubric_status")
        );
    }

    private RubricCriterionRow mapRubricCriterion(ResultSet rs, int rowNum) throws SQLException {
        return new RubricCriterionRow(
                rs.getLong("rubric_criterion_id"),
                rs.getLong("rubric_id"),
                rs.getInt("criterion_order"),
                rs.getString("criterion_name"),
                rs.getString("criterion_description"),
                rs.getBigDecimal("maximum_points"),
                parseJson(rs.getString("level_definition")),
                rs.getBoolean("is_required")
        );
    }

    private long insertAndReturnId(String sql, Object... parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return a generated identifier.");
        }
        return key.longValue();
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored rubric JSON is invalid.", exception);
        }
    }

    private boolean exists(String sql, Object... parameters) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + sql + ") v3_exists",
                Integer.class,
                parameters
        );
        return count != null && count > 0;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
