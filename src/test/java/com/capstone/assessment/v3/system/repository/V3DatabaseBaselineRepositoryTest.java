package com.capstone.assessment.v3.system.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V3DatabaseBaselineRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final V3DatabaseBaselineRepository repository = new V3DatabaseBaselineRepository(jdbcTemplate);

    @Test
    void readsEveryHardenedBaselineSignal() {
        stubDatabaseName();
        stubIntegerQueries(6);

        V3DatabaseBaselineRepository.V3DatabaseBaselineSnapshot snapshot = repository.readSnapshot();

        assertEquals("performance_assessment_v3_db", snapshot.databaseName());
        assertEquals(67, snapshot.tableCount());
        assertEquals(163, snapshot.foreignKeyCount());
        assertEquals(87, snapshot.checkConstraintCount());
        assertEquals(99, snapshot.uniqueConstraintCount());
        assertEquals(6, snapshot.requiredDynamicTableCount());
        assertEquals(16, snapshot.requiredHardeningColumnCount());
        assertEquals(15, snapshot.requiredHardeningConstraintCount());
        assertEquals(3, snapshot.schoolScopedSectionRuleCount());
        assertEquals(20, snapshot.academicCalendarColumnCount());
        assertEquals(16, snapshot.academicCalendarConstraintCount());
        assertEquals(3, snapshot.paperSizeCount());
        assertEquals(3, snapshot.requiredPaperSizeSeedCount());
        assertEquals(15, snapshot.validatedTemplateRegionCount());
        assertEquals(5, snapshot.activeQuestionTypeCount());
        assertEquals(1, snapshot.activeOmrTemplateCount());
        assertEquals(1, snapshot.approvedActiveOmrTemplateCount());
        assertEquals(0, snapshot.unapprovedActiveOmrTemplateCount());
        assertEquals(4, snapshot.activePerformanceRuleSetCount());
    }

    @Test
    void avoidsQueryingMissingDynamicTablesAndReportsTheirSignalsAsAbsent() {
        stubDatabaseName();
        stubIntegerQueries(5);

        V3DatabaseBaselineRepository.V3DatabaseBaselineSnapshot snapshot = repository.readSnapshot();

        assertEquals(5, snapshot.requiredDynamicTableCount());
        assertEquals(0, snapshot.paperSizeCount());
        assertEquals(0, snapshot.requiredPaperSizeSeedCount());
        assertEquals(0, snapshot.validatedTemplateRegionCount());
        assertEquals(0, snapshot.approvedActiveOmrTemplateCount());
        assertEquals(1, snapshot.unapprovedActiveOmrTemplateCount());
    }

    private void stubDatabaseName() {
        when(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .thenReturn("performance_assessment_v3_db");
    }

    private void stubIntegerQueries(int dynamicTableCount) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenAnswer(invocation -> countFor(invocation.getArgument(0), dynamicTableCount));
    }

    private int countFor(String rawSql, int dynamicTableCount) {
        String sql = rawSql.replaceAll("\\s+", " ").trim();
        if (sql.contains("fk_academic_years_school")) {
            return 16;
        }
        if (sql.contains("fk_sections_school")) {
            return 2;
        }
        if (sql.contains("constraint_name IN")) {
            return 15;
        }
        if (sql.contains("constraint_type = 'FOREIGN KEY'")) {
            return 163;
        }
        if (sql.contains("constraint_type = 'CHECK'")) {
            return 87;
        }
        if (sql.contains("constraint_type = 'UNIQUE'")) {
            return 99;
        }
        if (sql.contains("FROM information_schema.columns")) {
            if (sql.contains("class_assignment_schedules")) {
                return 20;
            }
            if (sql.contains("table_name = 'sections'")) {
                return 1;
            }
            return 16;
        }
        if (sql.contains("table_name IN")) {
            return dynamicTableCount;
        }
        if (sql.contains("FROM information_schema.tables")) {
            return 67;
        }
        if (sql.contains("FROM paper_sizes") && sql.contains("WHERE")) {
            return 3;
        }
        if (sql.contains("FROM paper_sizes")) {
            return 3;
        }
        if (sql.contains("FROM omr_template_regions")) {
            return 15;
        }
        if (sql.contains("FROM omr_templates template") && sql.contains("AND NOT (")) {
            return 0;
        }
        if (sql.contains("FROM omr_templates template")) {
            return 1;
        }
        if (sql.contains("FROM omr_templates")) {
            return 1;
        }
        if (sql.contains("FROM question_types")) {
            return 5;
        }
        if (sql.contains("FROM performance_rule_sets")) {
            return 4;
        }
        throw new AssertionError("Unexpected readiness query: " + sql);
    }
}
