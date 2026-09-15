package com.capstone.assessment.v3.system.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Profile("v3")
@Repository
public class V3DatabaseBaselineRepository {

    private static final int REQUIRED_DYNAMIC_TABLE_COUNT = 6;

    private final JdbcTemplate jdbcTemplate;

    public V3DatabaseBaselineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public V3DatabaseBaselineSnapshot readSnapshot() {
        String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        int tableCount = count("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                """);
        int foreignKeyCount = countConstraint("FOREIGN KEY");
        int checkConstraintCount = countConstraint("CHECK");
        int uniqueConstraintCount = countConstraint("UNIQUE");
        int requiredDynamicTableCount = count("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name IN (
                      'paper_sizes',
                      'omr_template_regions',
                      'answer_sheet_versions',
                      'answer_sheet_pages',
                      'answer_sheet_regions',
                      'scan_pages'
                  )
                """);
        int requiredHardeningColumnCount = count("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND (
                      table_name = 'questions'
                          AND column_name = 'expected_response_count'
                      OR table_name = 'answer_sheet_regions'
                          AND column_name IN (
                              'expected_response_count_snapshot',
                              'response_line_count'
                          )
                      OR table_name = 'answer_attachments'
                          AND column_name IN (
                              'source_answer_attachment_id',
                              'retention_policy_code',
                              'retention_until',
                              'retention_hold',
                              'retention_hold_reason',
                              'retention_hold_set_by_user_id',
                              'retention_hold_set_at',
                              'purge_status',
                              'last_purge_attempt_at',
                              'purged_at',
                              'purged_by_user_id',
                              'purge_reason',
                              'last_purge_error'
                          )
                  )
                """);
        int requiredHardeningConstraintCount = count("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name IN (
                      'fk_answer_attachments_source',
                      'fk_answer_attachments_hold_set_by_user',
                      'fk_answer_attachments_purged_by_user',
                      'chk_questions_expected_response_count',
                      'chk_answer_sheet_regions_expected_count',
                      'chk_answer_sheet_regions_line_count',
                      'chk_answer_sheet_regions_response_shape',
                      'chk_answer_attachments_normalized_source',
                      'chk_answer_attachments_hold',
                      'chk_answer_attachments_purge_state',
                      'chk_answer_attachments_held_not_purged',
                      'chk_answer_sheet_pages_qr_payload',
                      'chk_answer_sheet_pages_qr_hash',
                      'chk_scan_pages_qr_payload',
                      'chk_scan_pages_qr_hash'
                  )
                """);
        int schoolScopedSectionRuleCount = count("""
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND table_name = 'sections'
                   AND column_name = 'school_id'
                """) + count("""
                SELECT COUNT(*)
                  FROM information_schema.table_constraints
                 WHERE constraint_schema = DATABASE()
                   AND table_name = 'sections'
                   AND constraint_name IN (
                       'fk_sections_school',
                       'uk_sections_school_grade_name'
                   )
                """);
        int academicCalendarColumnCount = count("""
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND (
                       table_name = 'academic_years'
                           AND column_name IN ('school_id', 'active_school_id')
                       OR table_name = 'term_periods'
                           AND column_name = 'active_academic_year_id'
                       OR table_name = 'class_assignment_schedules'
                           AND column_name IN (
                               'schedule_uuid',
                               'class_assignment_id',
                               'day_of_week',
                               'start_time',
                               'end_time',
                               'timezone_name',
                               'effective_from',
                               'effective_to',
                               'schedule_status',
                               'created_by_user_id',
                               'status_changed_by_user_id',
                               'status_reason',
                               'archived_at'
                           )
                       OR table_name = 'test_assignments'
                           AND column_name IN (
                               'outside_schedule_confirmed',
                               'outside_schedule_reason',
                               'outside_schedule_confirmed_by_user_id',
                               'outside_schedule_confirmed_at'
                           )
                   )
                """);
        int academicCalendarConstraintCount = count("""
                SELECT COUNT(*)
                  FROM information_schema.table_constraints
                 WHERE constraint_schema = DATABASE()
                   AND constraint_name IN (
                       'fk_academic_years_school',
                       'uk_academic_years_school_name',
                       'uk_academic_years_one_active_school',
                       'uk_term_periods_one_active_year',
                       'chk_term_periods_order',
                       'uk_class_assignment_schedules_uuid',
                       'fk_class_assignment_schedules_assignment',
                       'fk_class_assignment_schedules_created_by_user',
                       'fk_class_assignment_schedules_status_user',
                       'chk_class_assignment_schedules_day',
                       'chk_class_assignment_schedules_time',
                       'chk_class_assignment_schedules_dates',
                       'chk_class_assignment_schedules_timezone',
                       'chk_class_assignment_schedules_archive',
                       'fk_test_assignments_outside_schedule_user',
                       'chk_test_assignments_outside_schedule_confirmation'
                   )
                """);
        int activeOmrTemplateCount = count(
                "SELECT COUNT(*) FROM omr_templates WHERE template_status = 'active'"
        );

        int paperSizeCount = 0;
        int requiredPaperSizeSeedCount = 0;
        int validatedTemplateRegionCount = 0;
        int approvedActiveOmrTemplateCount = 0;
        int unapprovedActiveOmrTemplateCount = activeOmrTemplateCount;
        if (requiredDynamicTableCount == REQUIRED_DYNAMIC_TABLE_COUNT) {
            paperSizeCount = count("SELECT COUNT(*) FROM paper_sizes");
            requiredPaperSizeSeedCount = count("""
                    SELECT COUNT(*)
                    FROM paper_sizes
                    WHERE (paper_size_code = 'A4'
                               AND width_points = 595.276
                               AND height_points = 841.890)
                       OR (paper_size_code = 'US_LETTER'
                               AND width_points = 612.000
                               AND height_points = 792.000)
                       OR (paper_size_code = 'US_LEGAL'
                               AND width_points = 612.000
                               AND height_points = 1008.000)
                    """);
            validatedTemplateRegionCount = count("""
                    SELECT COUNT(*)
                    FROM omr_template_regions region
                    JOIN omr_templates template
                      ON template.omr_template_id = region.omr_template_id
                    WHERE template.template_code = 'OMR-A4-10-MC-CTX-V2'
                      AND template.template_version = '2'
                    """);
            approvedActiveOmrTemplateCount = count("""
                    SELECT COUNT(*)
                    FROM omr_templates template
                    JOIN paper_sizes paper
                      ON paper.paper_size_id = template.paper_size_id
                    WHERE template.template_status = 'active'
                      AND ((template.template_code = 'OMR-A4-10-MC-CTX-V2' AND template.template_version = '2')
                        OR (template.template_code = 'OMR-A4-DYNAMIC-CTX-V3' AND template.template_version = '3' AND template.qr_payload_version=3))
                      AND paper.paper_size_code = 'A4'
                    """);
            unapprovedActiveOmrTemplateCount = count("""
                    SELECT COUNT(*)
                    FROM omr_templates template
                    JOIN paper_sizes paper
                      ON paper.paper_size_id = template.paper_size_id
                    WHERE template.template_status = 'active'
                      AND NOT (
                          ((template.template_code = 'OMR-A4-10-MC-CTX-V2' AND template.template_version = '2')
                            OR (template.template_code = 'OMR-A4-DYNAMIC-CTX-V3' AND template.template_version = '3' AND template.qr_payload_version=3))
                          AND paper.paper_size_code = 'A4'
                      )
                    """);
        }

        return new V3DatabaseBaselineSnapshot(
                databaseName,
                tableCount,
                foreignKeyCount,
                checkConstraintCount,
                uniqueConstraintCount,
                requiredDynamicTableCount,
                requiredHardeningColumnCount,
                requiredHardeningConstraintCount,
                schoolScopedSectionRuleCount,
                academicCalendarColumnCount,
                academicCalendarConstraintCount,
                paperSizeCount,
                requiredPaperSizeSeedCount,
                validatedTemplateRegionCount,
                count("SELECT COUNT(*) FROM question_types WHERE is_active = 1"),
                activeOmrTemplateCount,
                approvedActiveOmrTemplateCount,
                unapprovedActiveOmrTemplateCount,
                count("SELECT COUNT(*) FROM performance_rule_sets WHERE rule_status = 'active'")
        );
    }

    private int countConstraint(String constraintType) {
        String typePredicate = switch (constraintType) {
            case "FOREIGN KEY" -> "constraint_type = 'FOREIGN KEY'";
            case "CHECK" -> "constraint_type = 'CHECK'";
            case "UNIQUE" -> "constraint_type = 'UNIQUE'";
            default -> throw new IllegalArgumentException("Unsupported constraint type: " + constraintType);
        };
        return count("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND %s
                """.formatted(typePredicate));
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    public record V3DatabaseBaselineSnapshot(
            String databaseName,
            int tableCount,
            int foreignKeyCount,
            int checkConstraintCount,
            int uniqueConstraintCount,
            int requiredDynamicTableCount,
            int requiredHardeningColumnCount,
            int requiredHardeningConstraintCount,
            int schoolScopedSectionRuleCount,
            int academicCalendarColumnCount,
            int academicCalendarConstraintCount,
            int paperSizeCount,
            int requiredPaperSizeSeedCount,
            int validatedTemplateRegionCount,
            int activeQuestionTypeCount,
            int activeOmrTemplateCount,
            int approvedActiveOmrTemplateCount,
            int unapprovedActiveOmrTemplateCount,
            int activePerformanceRuleSetCount
    ) {
    }
}
