package com.capstone.assessment.v3.system.service;

import com.capstone.assessment.v3.system.config.V3BaselineProperties;
import com.capstone.assessment.v3.system.dto.V3DatabaseReadinessResponse;
import com.capstone.assessment.v3.system.repository.V3DatabaseBaselineRepository;
import com.capstone.assessment.v3.system.repository.V3DatabaseBaselineRepository.V3DatabaseBaselineSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V3DatabaseReadinessServiceTest {

    private static final int READINESS_CHECK_COUNT = 19;

    private final V3DatabaseBaselineRepository repository = mock(V3DatabaseBaselineRepository.class);
    private final V3BaselineProperties properties = new V3BaselineProperties();
    private final V3DatabaseReadinessService service = new V3DatabaseReadinessService(repository, properties);

    @Test
    void acceptsOnlyTheHardenedV3Baseline() {
        when(repository.readSnapshot()).thenReturn(snapshot(null));

        V3DatabaseReadinessResponse response = service.requireReady();

        assertTrue(response.ready());
        assertEquals(READINESS_CHECK_COUNT, response.checks().size());
        assertTrue(response.checks().stream().allMatch(check -> check.passed()));
    }

    @ParameterizedTest
    @EnumSource(ReadinessMismatch.class)
    void rejectsEveryRequiredBaselineMismatch(ReadinessMismatch mismatch) {
        when(repository.readSnapshot()).thenReturn(snapshot(mismatch));

        V3DatabaseReadinessResponse response = service.checkReadiness();

        assertFalse(response.ready());
        assertTrue(response.checks().stream().anyMatch(check ->
                check.check().equals(mismatch.checkName) && !check.passed()
        ));
        assertThrows(IllegalStateException.class, service::requireReady);
    }

    private V3DatabaseBaselineSnapshot snapshot(ReadinessMismatch mismatch) {
        String databaseName = "performance_assessment_v3_db";
        int tableCount = 67;
        int foreignKeyCount = 163;
        int checkConstraintCount = 87;
        int uniqueConstraintCount = 99;
        int requiredDynamicTableCount = 6;
        int requiredHardeningColumnCount = 16;
        int requiredHardeningConstraintCount = 15;
        int schoolScopedSectionRuleCount = 3;
        int academicCalendarColumnCount = 20;
        int academicCalendarConstraintCount = 16;
        int paperSizeCount = 3;
        int requiredPaperSizeSeedCount = 3;
        int validatedTemplateRegionCount = 15;
        int questionTypeCount = 5;
        int activeOmrTemplateCount = 1;
        int approvedActiveOmrTemplateCount = 1;
        int unapprovedActiveOmrTemplateCount = 0;
        int performanceRuleSetCount = 4;

        if (mismatch != null) {
            switch (mismatch) {
                case DATABASE -> databaseName = "performance_assessment_v2_db";
                case TABLES -> tableCount = 65;
                case FOREIGN_KEYS -> foreignKeyCount = 156;
                case CHECKS -> checkConstraintCount = 79;
                case UNIQUES -> uniqueConstraintCount = 95;
                case DYNAMIC_TABLES -> requiredDynamicTableCount = 5;
                case HARDENING_COLUMNS -> requiredHardeningColumnCount = 15;
                case HARDENING_CONSTRAINTS -> requiredHardeningConstraintCount = 14;
                case SCHOOL_SCOPED_SECTIONS -> schoolScopedSectionRuleCount = 2;
                case ACADEMIC_CALENDAR_COLUMNS -> academicCalendarColumnCount = 19;
                case ACADEMIC_CALENDAR_CONSTRAINTS -> academicCalendarConstraintCount = 15;
                case PAPER_SIZE_COUNT -> paperSizeCount = 4;
                case PAPER_SIZE_SEEDS -> requiredPaperSizeSeedCount = 2;
                case TEMPLATE_REGIONS -> validatedTemplateRegionCount = 14;
                case QUESTION_TYPES -> questionTypeCount = 4;
                case ACTIVE_TEMPLATES -> activeOmrTemplateCount = 2;
                case APPROVED_TEMPLATE -> approvedActiveOmrTemplateCount = 0;
                case UNAPPROVED_TEMPLATE -> unapprovedActiveOmrTemplateCount = 1;
                case RULE_SETS -> performanceRuleSetCount = 3;
            }
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
                questionTypeCount,
                activeOmrTemplateCount,
                approvedActiveOmrTemplateCount,
                unapprovedActiveOmrTemplateCount,
                performanceRuleSetCount
        );
    }

    private enum ReadinessMismatch {
        DATABASE("database_name"),
        TABLES("central_table_count"),
        FOREIGN_KEYS("foreign_key_count"),
        CHECKS("check_constraint_count"),
        UNIQUES("unique_constraint_count"),
        DYNAMIC_TABLES("required_dynamic_table_count"),
        HARDENING_COLUMNS("required_hardening_column_count"),
        HARDENING_CONSTRAINTS("required_hardening_constraint_count"),
        SCHOOL_SCOPED_SECTIONS("school_scoped_section_rule_count"),
        ACADEMIC_CALENDAR_COLUMNS("academic_calendar_column_count"),
        ACADEMIC_CALENDAR_CONSTRAINTS("academic_calendar_constraint_count"),
        PAPER_SIZE_COUNT("paper_size_count"),
        PAPER_SIZE_SEEDS("required_paper_size_seed_count"),
        TEMPLATE_REGIONS("validated_template_region_count"),
        QUESTION_TYPES("active_question_type_count"),
        ACTIVE_TEMPLATES("active_omr_template_count"),
        APPROVED_TEMPLATE("approved_active_omr_template_count"),
        UNAPPROVED_TEMPLATE("unapproved_active_omr_template_count"),
        RULE_SETS("active_performance_rule_set_count");

        private final String checkName;

        ReadinessMismatch(String checkName) {
            this.checkName = checkName;
        }
    }
}
