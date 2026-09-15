package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.mobile.dto.V3MobileDownloadResponse;
import com.capstone.assessment.v3.mobile.dto.V3MobileReferenceDataResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class V3MobileRepository {

    private static final String VALIDATED_TEMPLATE_CODE = com.capstone.assessment.v3.answersheet.service.V3DynamicLayout.CODE;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public V3MobileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<V3MobileReferenceDataResponse.QuestionTypeCapability> findQuestionTypes() {
        return jdbcTemplate.query(
                """
                SELECT question_type_id, question_type_code, question_type_name,
                       capture_mode, scoring_mode, supports_omr, supports_ocr,
                       supports_multiple_response, requires_attachment,
                       requires_teacher_verification, allows_teacher_answer_edit
                  FROM question_types
                 WHERE is_active = TRUE
                 ORDER BY question_type_id
                """,
                (rs, rowNumber) -> new V3MobileReferenceDataResponse.QuestionTypeCapability(
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
                        rs.getBoolean("allows_teacher_answer_edit")
                )
        );
    }

    public List<V3MobileReferenceDataResponse.PaperSizeCapability> findPaperSizes() {
        return jdbcTemplate.query(
                """
                SELECT ps.paper_size_id, ps.paper_size_code, ps.paper_size_name,
                       ps.width_points, ps.height_points,
                       EXISTS (
                           SELECT 1
                             FROM omr_templates template
                            WHERE template.paper_size_id = ps.paper_size_id
                              AND template.template_code = ?
                              AND template.template_status = 'active'
                       ) AS operationally_supported
                  FROM paper_sizes ps
                 WHERE ps.is_active = TRUE
                 ORDER BY ps.paper_size_id
                """,
                (rs, rowNumber) -> new V3MobileReferenceDataResponse.PaperSizeCapability(
                        rs.getInt("paper_size_id"),
                        rs.getString("paper_size_code"),
                        rs.getString("paper_size_name"),
                        rs.getBigDecimal("width_points"),
                        rs.getBigDecimal("height_points"),
                        rs.getBoolean("operationally_supported")
                ),
                VALIDATED_TEMPLATE_CODE
        );
    }

    public List<TemplateRow> findActiveTemplates() {
        return jdbcTemplate.query(
                """
                SELECT template.omr_template_id, template.template_code,
                       template.template_name, template.template_version,
                       type.question_type_code, size.paper_size_code,
                       template.page_orientation, template.minimum_item_count,
                       template.maximum_item_count, template.option_count,
                       template.qr_payload_version, template.minimum_scanner_version,
                       template.coordinate_origin, template.required_print_scale_percent,
                       template.geometry_hash
                  FROM omr_templates template
                  JOIN paper_sizes size ON size.paper_size_id = template.paper_size_id
                  LEFT JOIN question_types type
                    ON type.question_type_id = template.question_type_id
                 WHERE template.template_status = 'active'
                 ORDER BY template.omr_template_id
                """,
                (rs, rowNumber) -> new TemplateRow(
                        rs.getLong("omr_template_id"),
                        rs.getString("template_code"),
                        rs.getString("template_name"),
                        rs.getString("template_version"),
                        rs.getString("question_type_code"),
                        rs.getString("paper_size_code"),
                        rs.getString("page_orientation"),
                        nullableInteger(rs, "minimum_item_count"),
                        nullableInteger(rs, "maximum_item_count"),
                        nullableInteger(rs, "option_count"),
                        rs.getInt("qr_payload_version"),
                        rs.getString("minimum_scanner_version"),
                        rs.getString("coordinate_origin"),
                        rs.getBigDecimal("required_print_scale_percent"),
                        rs.getString("geometry_hash"),
                        VALIDATED_TEMPLATE_CODE.equals(rs.getString("template_code"))
                )
        );
    }

    public List<V3MobileReferenceDataResponse.TemplateRegion> findTemplateRegions(long templateId) {
        return jdbcTemplate.query(
                """
                SELECT region.region_uuid, region.region_code, region.region_order,
                       region.region_type, type.question_type_code,
                       region.layout_variant, region.response_region_size,
                       region.x_points, region.y_points, region.width_points,
                       region.height_points, region.geometry_definition,
                       region.geometry_hash, region.is_required
                  FROM omr_template_regions region
                  LEFT JOIN question_types type
                    ON type.question_type_id = region.question_type_id
                 WHERE region.omr_template_id = ?
                 ORDER BY region.region_order
                """,
                (rs, rowNumber) -> new V3MobileReferenceDataResponse.TemplateRegion(
                        rs.getString("region_uuid"),
                        rs.getString("region_code"),
                        rs.getInt("region_order"),
                        rs.getString("region_type"),
                        rs.getString("question_type_code"),
                        rs.getString("layout_variant"),
                        rs.getString("response_region_size"),
                        new V3MobileReferenceDataResponse.Rectangle(
                                rs.getBigDecimal("x_points"),
                                rs.getBigDecimal("y_points"),
                                rs.getBigDecimal("width_points"),
                                rs.getBigDecimal("height_points")
                        ),
                        parseJson(rs.getString("geometry_definition")),
                        rs.getString("geometry_hash"),
                        rs.getBoolean("is_required")
                ),
                templateId
        );
    }

    public List<V3MobileDownloadResponse.ClassAssignment> findClassAssignments(
            long teacherUserId,
            String schoolId
    ) {
        return jdbcTemplate.query(
                """
                SELECT assignment.class_assignment_id, assignment.class_id,
                       cohort.academic_year_id, academic_year.year_name,
                       section.grade_level_id, grade.grade_level_name,
                       section.section_id, section.section_name,
                       subject.subject_id, subject.subject_code, subject.subject_name,
                       assignment.assignment_role, assignment.status AS assignment_status,
                       cohort.status AS class_status
                  FROM class_assignments assignment
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  JOIN classes cohort ON cohort.class_id = assignment.class_id
                  JOIN academic_years academic_year
                    ON academic_year.academic_year_id = cohort.academic_year_id
                  JOIN sections section ON section.section_id = cohort.section_id
                  JOIN grade_levels grade
                    ON grade.grade_level_id = section.grade_level_id
                  JOIN subjects subject ON subject.subject_id = assignment.subject_id
                 WHERE assignment.user_id = ?
                   AND teacher.school_id = ?
                   AND assignment.status = 'active'
                   AND cohort.status = 'active'
                 ORDER BY academic_year.start_date DESC, grade.grade_level_name,
                          section.section_name, subject.subject_name
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.ClassAssignment(
                        rs.getLong("class_assignment_id"),
                        rs.getLong("class_id"),
                        rs.getInt("academic_year_id"),
                        rs.getString("year_name"),
                        rs.getInt("grade_level_id"),
                        rs.getString("grade_level_name"),
                        rs.getInt("section_id"),
                        rs.getString("section_name"),
                        rs.getInt("subject_id"),
                        rs.getString("subject_code"),
                        rs.getString("subject_name"),
                        rs.getString("assignment_role"),
                        rs.getString("assignment_status"),
                        rs.getString("class_status")
                ),
                teacherUserId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.ClassAssignmentSchedule> findClassAssignmentSchedules(
            long teacherUserId,
            String schoolId
    ) {
        return jdbcTemplate.query(
                """
                SELECT schedule.class_assignment_schedule_id, schedule.schedule_uuid,
                       schedule.class_assignment_id, schedule.day_of_week,
                       schedule.start_time, schedule.end_time, schedule.timezone_name,
                       schedule.effective_from, schedule.effective_to,
                       schedule.schedule_status, schedule.updated_at
                  FROM class_assignment_schedules schedule
                  JOIN class_assignments assignment
                    ON assignment.class_assignment_id = schedule.class_assignment_id
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  JOIN classes cohort ON cohort.class_id = assignment.class_id
                 WHERE assignment.user_id = ?
                   AND teacher.school_id = ?
                   AND assignment.status = 'active'
                   AND cohort.status = 'active'
                 ORDER BY schedule.class_assignment_id, schedule.day_of_week,
                          schedule.start_time, schedule.class_assignment_schedule_id
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.ClassAssignmentSchedule(
                        rs.getLong("class_assignment_schedule_id"),
                        rs.getString("schedule_uuid"),
                        rs.getLong("class_assignment_id"),
                        rs.getInt("day_of_week"),
                        rs.getTime("start_time").toLocalTime(),
                        rs.getTime("end_time").toLocalTime(),
                        rs.getString("timezone_name"),
                        rs.getDate("effective_from").toLocalDate(),
                        rs.getDate("effective_to") == null
                                ? null
                                : rs.getDate("effective_to").toLocalDate(),
                        rs.getString("schedule_status"),
                        toInstant(rs.getTimestamp("updated_at"))
                ),
                teacherUserId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.ClassListMembership> findClassLists(
            long teacherUserId,
            String schoolId
    ) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT membership.class_list_id, membership.membership_uuid,
                       membership.class_id, membership.student_id,
                       membership.enrollment_status, membership.enrollment_source,
                       membership.enrolled_at
                  FROM class_lists membership
                  JOIN students student ON student.student_id = membership.student_id
                 WHERE membership.enrollment_status = 'enrolled'
                   AND student.school_id = ?
                   AND EXISTS (
                       SELECT 1
                         FROM class_assignments assignment
                         JOIN users teacher ON teacher.user_id = assignment.user_id
                        WHERE assignment.class_id = membership.class_id
                          AND assignment.user_id = ?
                          AND teacher.school_id = ?
                          AND assignment.status = 'active'
                   )
                 ORDER BY membership.class_id, membership.class_list_id
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.ClassListMembership(
                        rs.getLong("class_list_id"),
                        rs.getString("membership_uuid"),
                        rs.getLong("class_id"),
                        rs.getLong("student_id"),
                        rs.getString("enrollment_status"),
                        rs.getString("enrollment_source"),
                        toInstant(rs.getTimestamp("enrolled_at"))
                ),
                schoolId,
                teacherUserId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.Student> findStudents(long teacherUserId, String schoolId) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT student.student_id, student.student_lrn,
                       student.first_name, student.middle_name, student.last_name,
                       suffix.suffix_name, gender.gender_name, student.status
                  FROM students student
                  JOIN genders gender ON gender.gender_id = student.gender_id
                  LEFT JOIN suffixes suffix ON suffix.suffix_id = student.suffix_id
                  JOIN class_lists membership ON membership.student_id = student.student_id
                 WHERE student.school_id = ?
                   AND membership.enrollment_status = 'enrolled'
                   AND EXISTS (
                       SELECT 1
                         FROM class_assignments assignment
                         JOIN users teacher ON teacher.user_id = assignment.user_id
                        WHERE assignment.class_id = membership.class_id
                          AND assignment.user_id = ?
                          AND teacher.school_id = ?
                          AND assignment.status = 'active'
                   )
                 ORDER BY student.last_name, student.first_name, student.student_id
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.Student(
                        rs.getLong("student_id"),
                        rs.getString("student_lrn"),
                        rs.getString("first_name"),
                        rs.getString("middle_name"),
                        rs.getString("last_name"),
                        rs.getString("suffix_name"),
                        rs.getString("gender_name"),
                        rs.getString("status")
                ),
                schoolId,
                teacherUserId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.TermPeriod> findTermPeriods(
            long teacherUserId,
            String schoolId
    ) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT term.term_period_id, term.academic_year_id,
                       term.term_name, term.term_order, term.start_at,
                       term.end_at, term.status
                  FROM term_periods term
                  JOIN tests assessment ON assessment.term_period_id = term.term_period_id
                 WHERE assessment.school_id = ?
                   AND EXISTS (
                       SELECT 1
                         FROM test_assignments delivery
                         JOIN class_assignments assignment
                           ON assignment.class_assignment_id = delivery.class_assignment_id
                         JOIN users teacher ON teacher.user_id = assignment.user_id
                        WHERE delivery.test_id = assessment.test_id
                          AND assignment.user_id = ?
                          AND teacher.school_id = ?
                          AND delivery.assignment_status <> 'archived'
                   )
                 ORDER BY term.academic_year_id, term.term_order
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.TermPeriod(
                        rs.getInt("term_period_id"),
                        rs.getInt("academic_year_id"),
                        rs.getString("term_name"),
                        rs.getInt("term_order"),
                        toInstant(rs.getTimestamp("start_at")),
                        toInstant(rs.getTimestamp("end_at")),
                        rs.getString("status")
                ),
                schoolId,
                teacherUserId,
                schoolId
        );
    }

    public List<TestAssignmentRow> findTestAssignments(long teacherUserId, String schoolId) {
        return jdbcTemplate.query(
                """
                SELECT delivery.test_assignment_id, delivery.assignment_uuid,
                       delivery.test_id, delivery.class_assignment_id,
                       delivery.open_at, delivery.close_at,
                       delivery.assignment_status, delivery.allow_late_capture
                  FROM test_assignments delivery
                  JOIN class_assignments assignment
                    ON assignment.class_assignment_id = delivery.class_assignment_id
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  JOIN tests assessment ON assessment.test_id = delivery.test_id
                 WHERE assignment.user_id = ?
                   AND teacher.school_id = ?
                   AND assessment.school_id = ?
                   AND assignment.status = 'active'
                   AND delivery.assignment_status <> 'archived'
                   AND assessment.status IN ('active', 'completed')
                 ORDER BY delivery.assigned_at DESC, delivery.test_assignment_id
                """,
                (rs, rowNumber) -> new TestAssignmentRow(
                        rs.getLong("test_assignment_id"),
                        rs.getString("assignment_uuid"),
                        rs.getLong("test_id"),
                        rs.getLong("class_assignment_id"),
                        toInstant(rs.getTimestamp("open_at")),
                        toInstant(rs.getTimestamp("close_at")),
                        rs.getString("assignment_status"),
                        rs.getBoolean("allow_late_capture")
                ),
                teacherUserId,
                schoolId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.Test> findTests(long teacherUserId, String schoolId) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT assessment.test_id, assessment.test_uuid, assessment.version_number,
                       assessment.term_period_id, assessment.test_name, assessment.test_type,
                       assessment.instructions, assessment.total_items, assessment.status
                  FROM tests assessment
                 WHERE assessment.school_id = ?
                   AND assessment.status IN ('active', 'completed')
                   AND EXISTS (
                       SELECT 1
                         FROM test_assignments delivery
                         JOIN class_assignments assignment
                           ON assignment.class_assignment_id = delivery.class_assignment_id
                         JOIN users teacher ON teacher.user_id = assignment.user_id
                        WHERE delivery.test_id = assessment.test_id
                          AND assignment.user_id = ?
                          AND teacher.school_id = ?
                          AND assignment.status = 'active'
                          AND delivery.assignment_status <> 'archived'
                   )
                 ORDER BY assessment.test_id
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.Test(
                        rs.getLong("test_id"),
                        rs.getString("test_uuid"),
                        rs.getInt("version_number"),
                        rs.getInt("term_period_id"),
                        rs.getString("test_name"),
                        rs.getString("test_type"),
                        rs.getString("instructions"),
                        rs.getInt("total_items"),
                        rs.getString("status")
                ),
                schoolId,
                teacherUserId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.TestPart> findTestParts(
            long teacherUserId,
            String schoolId
    ) {
        return jdbcTemplate.query(
                """
                SELECT part.test_part_id, part.test_id, part.part_order,
                       part.part_name, part.question_type_id, part.number_of_items,
                       part.points_per_item, part.part_instructions
                  FROM test_parts part
                  JOIN tests assessment ON assessment.test_id = part.test_id
                 WHERE assessment.school_id = ?
                   AND EXISTS (
                       SELECT 1
                         FROM test_assignments delivery
                         JOIN class_assignments assignment
                           ON assignment.class_assignment_id = delivery.class_assignment_id
                         JOIN users teacher ON teacher.user_id = assignment.user_id
                        WHERE delivery.test_id = part.test_id
                          AND assignment.user_id = ?
                          AND teacher.school_id = ?
                          AND assignment.status = 'active'
                          AND delivery.assignment_status <> 'archived'
                   )
                 ORDER BY part.test_id, part.part_order
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.TestPart(
                        rs.getLong("test_part_id"),
                        rs.getLong("test_id"),
                        rs.getInt("part_order"),
                        rs.getString("part_name"),
                        rs.getInt("question_type_id"),
                        rs.getInt("number_of_items"),
                        rs.getBigDecimal("points_per_item"),
                        rs.getString("part_instructions")
                ),
                schoolId,
                teacherUserId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.Question> findQuestions(
            long teacherUserId,
            String schoolId
    ) {
        return jdbcTemplate.query(
                """
                SELECT question.question_id, question.question_uuid,
                       question.test_part_id, question.question_type_id,
                       question.item_number,
                       ROW_NUMBER() OVER (
                           PARTITION BY part.test_id
                           ORDER BY part.part_order, question.item_number
                       ) AS global_item_number,
                       question.question_text, question.maximum_points,
                       question.rubric_id, question.response_instructions,
                       question.answer_order_required,
                       question.maximum_response_length,
                       question.expected_response_count,
                       question.response_region_size,
                       question.force_page_break_before
                  FROM questions question
                  JOIN test_parts part ON part.test_part_id = question.test_part_id
                  JOIN tests assessment ON assessment.test_id = part.test_id
                 WHERE assessment.school_id = ?
                   AND EXISTS (
                       SELECT 1
                         FROM test_assignments delivery
                         JOIN class_assignments assignment
                           ON assignment.class_assignment_id = delivery.class_assignment_id
                         JOIN users teacher ON teacher.user_id = assignment.user_id
                        WHERE delivery.test_id = part.test_id
                          AND assignment.user_id = ?
                          AND teacher.school_id = ?
                          AND assignment.status = 'active'
                          AND delivery.assignment_status <> 'archived'
                   )
                 ORDER BY part.test_id, part.part_order, question.item_number
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.Question(
                        rs.getLong("question_id"),
                        rs.getString("question_uuid"),
                        rs.getLong("test_part_id"),
                        rs.getInt("question_type_id"),
                        rs.getInt("item_number"),
                        rs.getInt("global_item_number"),
                        rs.getString("question_text"),
                        rs.getBigDecimal("maximum_points"),
                        nullableLong(rs, "rubric_id"),
                        rs.getString("response_instructions"),
                        rs.getBoolean("answer_order_required"),
                        nullableInteger(rs, "maximum_response_length"),
                        nullableInteger(rs, "expected_response_count"),
                        rs.getString("response_region_size"),
                        rs.getBoolean("force_page_break_before")
                ),
                schoolId,
                teacherUserId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.QuestionOption> findQuestionOptions(
            long teacherUserId,
            String schoolId
    ) {
        return jdbcTemplate.query(
                """
                SELECT question_option.question_option_id, question_option.question_id,
                       question_option.option_key, question_option.option_text, question_option.option_order
                  FROM question_options question_option
                  JOIN questions question ON question.question_id = question_option.question_id
                  JOIN test_parts part ON part.test_part_id = question.test_part_id
                  JOIN tests assessment ON assessment.test_id = part.test_id
                 WHERE question_option.is_active = TRUE
                   AND assessment.school_id = ?
                   AND EXISTS (
                       SELECT 1
                         FROM test_assignments delivery
                         JOIN class_assignments assignment
                           ON assignment.class_assignment_id = delivery.class_assignment_id
                         JOIN users teacher ON teacher.user_id = assignment.user_id
                        WHERE delivery.test_id = part.test_id
                          AND assignment.user_id = ?
                          AND teacher.school_id = ?
                          AND assignment.status = 'active'
                          AND delivery.assignment_status <> 'archived'
                   )
                 ORDER BY question_option.question_id, question_option.option_order
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.QuestionOption(
                        rs.getLong("question_option_id"),
                        rs.getLong("question_id"),
                        rs.getString("option_key"),
                        rs.getString("option_text"),
                        rs.getInt("option_order")
                ),
                schoolId,
                teacherUserId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.PartSkillMapping> findPartSkillMappings(
            long teacherUserId,
            String schoolId
    ) {
        return jdbcTemplate.query(
                """
                SELECT mapping.part_skill_mapping_id, mapping.test_part_id,
                       mapping.skill_id, mapping.start_item_number,
                       mapping.end_item_number, mapping.item_count
                  FROM part_skill_mappings mapping
                  JOIN test_parts part ON part.test_part_id = mapping.test_part_id
                  JOIN tests assessment ON assessment.test_id = part.test_id
                 WHERE assessment.school_id = ?
                   AND EXISTS (
                       SELECT 1
                         FROM test_assignments delivery
                         JOIN class_assignments assignment
                           ON assignment.class_assignment_id = delivery.class_assignment_id
                         JOIN users teacher ON teacher.user_id = assignment.user_id
                        WHERE delivery.test_id = part.test_id
                          AND assignment.user_id = ?
                          AND teacher.school_id = ?
                          AND assignment.status = 'active'
                          AND delivery.assignment_status <> 'archived'
                   )
                 ORDER BY mapping.test_part_id, mapping.start_item_number,
                          mapping.end_item_number
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.PartSkillMapping(
                        rs.getLong("part_skill_mapping_id"),
                        rs.getLong("test_part_id"),
                        rs.getLong("skill_id"),
                        rs.getInt("start_item_number"),
                        rs.getInt("end_item_number"),
                        rs.getInt("item_count")
                ),
                schoolId,
                teacherUserId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.Skill> findSkills(long teacherUserId, String schoolId) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT skill.skill_id, skill.competency_id,
                       competency.competency_name, root.root_tag_id,
                       root.root_tag_name, skill.term_period_id,
                       skill.grade_level_id, skill.subject_id
                  FROM skills skill
                  JOIN competency_tags competency
                    ON competency.competency_id = skill.competency_id
                  JOIN root_tags root ON root.root_tag_id = competency.root_tag_id
                  JOIN part_skill_mappings mapping ON mapping.skill_id = skill.skill_id
                  JOIN test_parts part ON part.test_part_id = mapping.test_part_id
                  JOIN tests assessment ON assessment.test_id = part.test_id
                 WHERE assessment.school_id = ?
                   AND EXISTS (
                       SELECT 1
                         FROM test_assignments delivery
                         JOIN class_assignments assignment
                           ON assignment.class_assignment_id = delivery.class_assignment_id
                         JOIN users teacher ON teacher.user_id = assignment.user_id
                        WHERE delivery.test_id = part.test_id
                          AND assignment.user_id = ?
                          AND teacher.school_id = ?
                          AND assignment.status = 'active'
                          AND delivery.assignment_status <> 'archived'
                   )
                 ORDER BY skill.skill_id
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.Skill(
                        rs.getLong("skill_id"),
                        rs.getLong("competency_id"),
                        rs.getString("competency_name"),
                        rs.getInt("root_tag_id"),
                        rs.getString("root_tag_name"),
                        rs.getInt("term_period_id"),
                        rs.getInt("grade_level_id"),
                        rs.getInt("subject_id")
                ),
                schoolId,
                teacherUserId,
                schoolId
        );
    }

    public List<V3MobileDownloadResponse.AnswerSheet> findAnswerSheets(
            long teacherUserId,
            String schoolId
    ) {
        return jdbcTemplate.query(
                """
                SELECT sheet.answer_sheet_uuid, sheet.test_assignment_id,
                       delivery.assignment_uuid, size.paper_size_code,
                       sheet.generation_number, sheet.test_version_number,
                       sheet.total_questions, sheet.total_pages,
                       sheet.manifest_version, sheet.manifest_hash,
                       COALESCE((
                           SELECT MAX(template.minimum_scanner_version)
                             FROM answer_sheet_pages page
                             JOIN omr_templates template
                               ON template.omr_template_id = page.omr_template_id
                            WHERE page.answer_sheet_version_id = sheet.answer_sheet_version_id
                              AND page.page_status = 'ready'
                       ), '') AS required_scanner_version,
                       sheet.generated_at
                  FROM answer_sheet_versions sheet
                  JOIN paper_sizes size ON size.paper_size_id = sheet.paper_size_id
                  JOIN test_assignments delivery
                    ON delivery.test_assignment_id = sheet.test_assignment_id
                  JOIN class_assignments assignment
                    ON assignment.class_assignment_id = delivery.class_assignment_id
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  JOIN tests assessment ON assessment.test_id = delivery.test_id
                 WHERE sheet.generation_status = 'ready'
                   AND assignment.user_id = ?
                   AND teacher.school_id = ?
                   AND assessment.school_id = ?
                   AND assignment.status = 'active'
                   AND delivery.assignment_status <> 'archived'
                 ORDER BY sheet.test_assignment_id, sheet.generation_number DESC
                """,
                (rs, rowNumber) -> new V3MobileDownloadResponse.AnswerSheet(
                        rs.getString("answer_sheet_uuid"),
                        rs.getLong("test_assignment_id"),
                        rs.getString("assignment_uuid"),
                        rs.getString("paper_size_code"),
                        rs.getInt("generation_number"),
                        rs.getInt("test_version_number"),
                        rs.getInt("total_questions"),
                        rs.getInt("total_pages"),
                        rs.getInt("manifest_version"),
                        rs.getString("manifest_hash"),
                        rs.getString("required_scanner_version"),
                        toInstant(rs.getTimestamp("generated_at"))
                ),
                teacherUserId,
                schoolId,
                schoolId
        );
    }

    public Optional<ManifestHeaderRow> findManifestHeader(
            String assignmentUuid,
            String answerSheetUuid,
            long teacherUserId,
            String schoolId
    ) {
        return jdbcTemplate.query(
                """
                SELECT sheet.answer_sheet_version_id, sheet.answer_sheet_uuid,
                       sheet.manifest_version, sheet.test_assignment_id,
                       delivery.assignment_uuid, size.paper_size_code,
                       size.width_points, size.height_points,
                       sheet.test_version_number, sheet.total_questions,
                       sheet.total_pages, sheet.manifest_hash, sheet.generated_at,
                       COALESCE((
                           SELECT MAX(template.minimum_scanner_version)
                             FROM answer_sheet_pages page
                             JOIN omr_templates template
                               ON template.omr_template_id = page.omr_template_id
                            WHERE page.answer_sheet_version_id = sheet.answer_sheet_version_id
                              AND page.page_status = 'ready'
                       ), '') AS required_scanner_version
                  FROM answer_sheet_versions sheet
                  JOIN paper_sizes size ON size.paper_size_id = sheet.paper_size_id
                  JOIN test_assignments delivery
                    ON delivery.test_assignment_id = sheet.test_assignment_id
                  JOIN class_assignments assignment
                    ON assignment.class_assignment_id = delivery.class_assignment_id
                  JOIN users teacher ON teacher.user_id = assignment.user_id
                  JOIN tests assessment ON assessment.test_id = delivery.test_id
                 WHERE delivery.assignment_uuid = ?
                   AND sheet.answer_sheet_uuid = ?
                   AND sheet.generation_status = 'ready'
                   AND assignment.user_id = ?
                   AND teacher.school_id = ?
                   AND assessment.school_id = ?
                """,
                (rs, rowNumber) -> new ManifestHeaderRow(
                        rs.getLong("answer_sheet_version_id"),
                        rs.getString("answer_sheet_uuid"),
                        rs.getInt("manifest_version"),
                        rs.getLong("test_assignment_id"),
                        rs.getString("assignment_uuid"),
                        rs.getString("paper_size_code"),
                        rs.getBigDecimal("width_points"),
                        rs.getBigDecimal("height_points"),
                        rs.getInt("test_version_number"),
                        rs.getInt("total_questions"),
                        rs.getInt("total_pages"),
                        rs.getString("manifest_hash"),
                        rs.getString("required_scanner_version"),
                        toInstant(rs.getTimestamp("generated_at"))
                ),
                assignmentUuid,
                answerSheetUuid,
                teacherUserId,
                schoolId,
                schoolId
        ).stream().findFirst();
    }

    public List<V3MobileReferenceDataResponse.TemplateRegion> pageTemplateRegions(long pageId) {
        Long templateId=jdbcTemplate.queryForObject("SELECT omr_template_id FROM answer_sheet_pages WHERE answer_sheet_page_id=?",Long.class,pageId);
        return findTemplateRegions(templateId);
    }

    public List<ManifestPageRow> findManifestPages(long answerSheetVersionId) {
        return jdbcTemplate.query(
                """
                SELECT page.answer_sheet_page_id, page.page_uuid,
                       page.page_number, page.total_pages,
                       template.template_code, template.template_version,
                       template.geometry_hash, template.page_orientation,
                       template.coordinate_origin, template.qr_payload_version,
                       page.qr_payload, page.qr_payload_hash,
                       page.page_geometry_hash
                  FROM answer_sheet_pages page
                  JOIN omr_templates template
                    ON template.omr_template_id = page.omr_template_id
                 WHERE page.answer_sheet_version_id = ?
                   AND page.page_status = 'ready'
                 ORDER BY page.page_number
                """,
                (rs, rowNumber) -> new ManifestPageRow(
                        rs.getLong("answer_sheet_page_id"),
                        rs.getString("page_uuid"),
                        rs.getInt("page_number"),
                        rs.getInt("total_pages"),
                        rs.getString("template_code"),
                        rs.getString("template_version"),
                        rs.getString("geometry_hash"),
                        rs.getString("page_orientation"),
                        rs.getString("coordinate_origin"),
                        rs.getInt("qr_payload_version"),
                        rs.getString("qr_payload"),
                        rs.getString("qr_payload_hash"),
                        rs.getString("page_geometry_hash")
                ),
                answerSheetVersionId
        );
    }

    public List<ManifestRegionRow> findManifestRegions(long answerSheetPageId) {
        return jdbcTemplate.query(
                """
                SELECT region.region_uuid, template_region.region_code,
                       region.question_id, question.question_uuid,
                       region.test_part_id, region.global_item_number,
                       region.part_item_number, type.question_type_code,
                       region.region_type, region.response_region_size,
                       region.expected_response_count_snapshot,
                       region.response_line_count, region.geometry_snapshot,
                       region.geometry_hash, template_region.x_points,
                       template_region.y_points, template_region.width_points,
                       template_region.height_points
                  FROM answer_sheet_regions region
                  JOIN omr_template_regions template_region
                    ON template_region.omr_template_region_id = region.omr_template_region_id
                  JOIN questions question ON question.question_id = region.question_id
                  JOIN question_types type
                    ON type.question_type_id = region.question_type_id
                 WHERE region.answer_sheet_page_id = ?
                 ORDER BY region.global_item_number, region.region_sequence
                """,
                (rs, rowNumber) -> new ManifestRegionRow(
                        rs.getString("region_uuid"),
                        rs.getString("region_code"),
                        rs.getLong("question_id"),
                        rs.getString("question_uuid"),
                        rs.getLong("test_part_id"),
                        rs.getInt("global_item_number"),
                        rs.getInt("part_item_number"),
                        rs.getString("question_type_code"),
                        rs.getString("region_type"),
                        rs.getString("response_region_size"),
                        nullableInteger(rs, "expected_response_count_snapshot"),
                        nullableInteger(rs, "response_line_count"),
                        parseJson(rs.getString("geometry_snapshot")),
                        rs.getString("geometry_hash"),
                        rs.getBigDecimal("x_points"),
                        rs.getBigDecimal("y_points"),
                        rs.getBigDecimal("width_points"),
                        rs.getBigDecimal("height_points")
                ),
                answerSheetPageId
        );
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored V3 mobile geometry is not valid JSON.", exception);
        }
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, Integer.class);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, Long.class);
    }

    public record TemplateRow(
            long omrTemplateId,
            String code,
            String name,
            String version,
            String questionType,
            String paperSize,
            String orientation,
            Integer minimumItemCount,
            Integer maximumItemCount,
            Integer optionCount,
            int qrPayloadVersion,
            String minimumScannerVersion,
            String coordinateOrigin,
            BigDecimal requiredPrintScalePercent,
            String geometryHash,
            boolean physicallyValidated
    ) {
    }

    public record TestAssignmentRow(
            long testAssignmentId,
            String assignmentUuid,
            long testId,
            long classAssignmentId,
            Instant openAt,
            Instant closeAt,
            String assignmentStatus,
            boolean allowLateCapture
    ) {
    }

    public record ManifestHeaderRow(
            long answerSheetVersionId,
            String answerSheetUuid,
            int manifestVersion,
            long testAssignmentId,
            String assignmentUuid,
            String paperSizeCode,
            BigDecimal widthPoints,
            BigDecimal heightPoints,
            int testVersionNumber,
            int totalQuestions,
            int totalPages,
            String manifestHash,
            String requiredScannerVersion,
            Instant generatedAt
    ) {
    }

    public record ManifestPageRow(
            long answerSheetPageId,
            String pageUuid,
            int pageNumber,
            int totalPages,
            String templateCode,
            String templateVersion,
            String templateGeometryHash,
            String orientation,
            String coordinateOrigin,
            int qrPayloadVersion,
            String qrPayload,
            String qrPayloadHash,
            String pageGeometryHash
    ) {
    }

    public record ManifestRegionRow(
            String regionUuid,
            String templateRegionCode,
            long questionId,
            String questionUuid,
            long testPartId,
            int globalItemNumber,
            int partItemNumber,
            String questionType,
            String regionType,
            String responseRegionSize,
            Integer expectedResponseCount,
            Integer responseLineCount,
            JsonNode geometry,
            String geometryHash,
            BigDecimal fallbackX,
            BigDecimal fallbackY,
            BigDecimal fallbackWidth,
            BigDecimal fallbackHeight
    ) {
    }
}
