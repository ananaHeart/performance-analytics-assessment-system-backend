package com.capstone.assessment.v3.system.service;

import com.capstone.assessment.v3.system.config.V3BaselineProperties;
import com.capstone.assessment.v3.system.dto.V3DatabaseCheckResponse;
import com.capstone.assessment.v3.system.dto.V3DatabaseReadinessResponse;
import com.capstone.assessment.v3.system.repository.V3DatabaseBaselineRepository;
import com.capstone.assessment.v3.system.repository.V3DatabaseBaselineRepository.V3DatabaseBaselineSnapshot;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Profile("v3")
@Service
public class V3DatabaseReadinessService {

    private final V3DatabaseBaselineRepository repository;
    private final V3BaselineProperties properties;

    public V3DatabaseReadinessService(
            V3DatabaseBaselineRepository repository,
            V3BaselineProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    public V3DatabaseReadinessResponse checkReadiness() {
        V3DatabaseBaselineSnapshot snapshot = repository.readSnapshot();
        List<V3DatabaseCheckResponse> checks = List.of(
                check("database_name", properties.getExpectedDatabase(), snapshot.databaseName()),
                check("central_table_count", properties.getExpectedTableCount(), snapshot.tableCount()),
                check("foreign_key_count", properties.getExpectedForeignKeyCount(), snapshot.foreignKeyCount()),
                check("check_constraint_count", properties.getExpectedCheckConstraintCount(), snapshot.checkConstraintCount()),
                check("unique_constraint_count", properties.getExpectedUniqueConstraintCount(), snapshot.uniqueConstraintCount()),
                check("required_dynamic_table_count", properties.getExpectedDynamicTableCount(), snapshot.requiredDynamicTableCount()),
                check("required_hardening_column_count", properties.getExpectedHardeningColumnCount(), snapshot.requiredHardeningColumnCount()),
                check("required_hardening_constraint_count", properties.getExpectedHardeningConstraintCount(), snapshot.requiredHardeningConstraintCount()),
                check("school_scoped_section_rule_count", properties.getExpectedSchoolScopedSectionRuleCount(), snapshot.schoolScopedSectionRuleCount()),
                check("academic_calendar_column_count", properties.getExpectedAcademicCalendarColumnCount(), snapshot.academicCalendarColumnCount()),
                check("academic_calendar_constraint_count", properties.getExpectedAcademicCalendarConstraintCount(), snapshot.academicCalendarConstraintCount()),
                check("paper_size_count", properties.getExpectedPaperSizeCount(), snapshot.paperSizeCount()),
                check("required_paper_size_seed_count", properties.getExpectedPaperSizeSeedCount(), snapshot.requiredPaperSizeSeedCount()),
                check("validated_template_region_count", properties.getExpectedValidatedTemplateRegionCount(), snapshot.validatedTemplateRegionCount()),
                check("active_question_type_count", properties.getExpectedQuestionTypeCount(), snapshot.activeQuestionTypeCount()),
                check("active_omr_template_count", properties.getExpectedOmrTemplateCount(), snapshot.activeOmrTemplateCount()),
                check("approved_active_omr_template_count", properties.getExpectedApprovedOmrTemplateCount(), snapshot.approvedActiveOmrTemplateCount()),
                check("unapproved_active_omr_template_count", properties.getExpectedUnapprovedOmrTemplateCount(), snapshot.unapprovedActiveOmrTemplateCount()),
                check("active_performance_rule_set_count", properties.getExpectedPerformanceRuleSetCount(), snapshot.activePerformanceRuleSetCount())
        );
        boolean ready = checks.stream().allMatch(V3DatabaseCheckResponse::passed);

        return new V3DatabaseReadinessResponse(
                "v3",
                snapshot.databaseName(),
                ready,
                snapshot.tableCount(),
                snapshot.foreignKeyCount(),
                snapshot.checkConstraintCount(),
                snapshot.uniqueConstraintCount(),
                snapshot.activeQuestionTypeCount(),
                snapshot.activeOmrTemplateCount(),
                snapshot.activePerformanceRuleSetCount(),
                checks
        );
    }

    public V3DatabaseReadinessResponse requireReady() {
        V3DatabaseReadinessResponse response = checkReadiness();
        if (!response.ready()) {
            String failures = response.checks().stream()
                    .filter(check -> !check.passed())
                    .map(check -> check.check() + " expected=" + check.expected() + " actual=" + check.actual())
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("unknown baseline failure");
            throw new IllegalStateException("V3 database baseline validation failed: " + failures);
        }
        return response;
    }

    private V3DatabaseCheckResponse check(String name, Object expected, Object actual) {
        String expectedValue = String.valueOf(expected);
        String actualValue = String.valueOf(actual);
        return new V3DatabaseCheckResponse(name, expectedValue, actualValue, expectedValue.equals(actualValue));
    }
}
