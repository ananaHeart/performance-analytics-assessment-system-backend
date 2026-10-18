package com.capstone.assessment.v3.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A teacher's learners who are below 80% mastery in at least one learning competency in one
 * term, across the teacher's own classes. Built from the learning-competency report's weak
 * students, so the numbers always match that report.
 */
public record V3StudentsNeedingSupportResponse(
        String reportType,
        Instant generatedAt,
        String schoolName,
        String teacherName,
        String academicYearName,
        int termPeriodId,
        String termName,
        String dataStatus,
        List<V3AssessmentResultsReportResponse.ReportWarning> warnings,
        // Lowest mastery first.
        List<Student> students
) {
    public V3StudentsNeedingSupportResponse {
        warnings = List.copyOf(warnings);
        students = List.copyOf(students);
    }

    public record Student(
            long studentId,
            String studentLrn,
            String fullName,
            String gradeLevelName,
            String sectionName,
            BigDecimal lowestMasteryPercentage,
            // Lowest mastery first.
            List<Competency> competencies
    ) {
        public Student {
            competencies = List.copyOf(competencies);
        }
    }

    public record Competency(
            String subjectName,
            String competencyName,
            BigDecimal masteryPercentage,
            String masteryStatusCode,
            String recommendationCode,
            String recommendationLabel,
            String suggestion
    ) {
    }
}
