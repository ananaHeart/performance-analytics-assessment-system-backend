package com.capstone.assessment.v3.assessment.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.capstone.assessment.v3.report.repository.V3ReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3TermWindowCompatibilityTest {
    private JdbcTemplate jdbc;
    private V3AssessmentRepository repository;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:calendar-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        repository = new V3AssessmentRepository(jdbc, new ObjectMapper());
        jdbc.execute("CREATE TABLE academic_years (academic_year_id INT PRIMARY KEY, school_id VARCHAR(32))");
        jdbc.execute("CREATE TABLE term_periods (term_period_id INT PRIMARY KEY, academic_year_id INT, "
                + "term_name VARCHAR(50), term_order INT, start_at TIMESTAMP, end_at TIMESTAMP, status VARCHAR(20))");
        jdbc.update("INSERT INTO academic_years VALUES (100, 'SCHOOL-001'), (200, 'SCHOOL-001')");
    }

    @AfterEach
    void close() {
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void completeThreeTermCalendarUsesExclusiveEndAndPreservesSchoolScope() {
        insertTerm(11, 100, "Term 1", 1);
        insertTerm(12, 100, "Term 2", 2);
        insertTerm(13, 100, "Term 3", 3);
        assertTrue(repository.findTermWindow(12, 100, "SCHOOL-001").orElseThrow().endExclusive());
        var reports = new V3ReportRepository(jdbc);
        assertEquals(repository.findTermWindow(12, 100, "SCHOOL-001").orElseThrow().endAt(),
                reports.findTermPeriodWindow(12, "SCHOOL-001").orElseThrow().endExclusive());
        assertTrue(reports.findTermPeriodWindow(12, "ANOTHER-SCHOOL").isEmpty());
        assertTrue(repository.findTermWindow(12, 100, "ANOTHER-SCHOOL").isEmpty());
        assertTrue(repository.findTermWindow(12, 200, "SCHOOL-001").isEmpty());
    }

    @Test
    void legacyFourQuarterCalendarPreservesInclusiveEnd() {
        insertTerm(21, 200, "First Quarter", 1);
        insertTerm(22, 200, "Second Quarter", 2);
        insertTerm(23, 200, "Third Quarter", 3);
        insertTerm(24, 200, "Fourth Quarter", 4);
        assertFalse(repository.findTermWindow(22, 200, "SCHOOL-001").orElseThrow().endExclusive());
        assertEquals(repository.findTermWindow(22, 200, "SCHOOL-001").orElseThrow().endAt().plusSeconds(1),
                new V3ReportRepository(jdbc).findTermPeriodWindow(22, "SCHOOL-001").orElseThrow().endExclusive());
    }

    @Test
    void aSingleTermNameCannotReinterpretHistoricalCalendarBoundaries() {
        insertTerm(11, 100, "Term 1", 1);
        insertTerm(12, 100, "Second Quarter", 2);
        insertTerm(13, 100, "Third Quarter", 3);
        insertTerm(14, 100, "Fourth Quarter", 4);
        assertFalse(repository.findTermWindow(11, 100, "SCHOOL-001").orElseThrow().endExclusive());
    }

    private void insertTerm(int id, int year, String name, int order) {
        jdbc.update("INSERT INTO term_periods VALUES (?, ?, ?, ?, TIMESTAMP '2026-09-16 00:00:00', "
                + "TIMESTAMP '2026-12-19 00:00:00', 'active')", id, year, name, order);
    }
}
