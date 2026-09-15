package com.capstone.assessment.v3.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record V3ReportReferenceDataResponse(
        String role,
        SchoolOption school,
        List<AcademicYearOption> academicYears,
        List<TermPeriodOption> termPeriods,
        List<GradeLevelOption> gradeLevels,
        List<ClassOption> classes,
        List<TeacherOption> teachers,
        List<SubjectOption> subjects,
        List<ClassAssignmentOption> classAssignments,
        List<AssessmentOption> assessments,
        List<StudentOption> students
) {

    public record SchoolOption(
            String schoolId,
            String schoolName
    ) {
    }

    public record AcademicYearOption(
            int academicYearId,
            String yearName,
            LocalDate startDate,
            LocalDate endDate,
            String status
    ) {
    }

    public record TermPeriodOption(
            int termPeriodId,
            int academicYearId,
            String termName,
            int termOrder,
            Instant startAt,
            Instant endAt,
            String status
    ) {
    }

    public record GradeLevelOption(
            int gradeLevelId,
            String gradeLevelName
    ) {
    }

    public record ClassOption(
            long classId,
            int academicYearId,
            String academicYearName,
            int gradeLevelId,
            String gradeLevelName,
            int sectionId,
            String sectionName,
            String status
    ) {
    }

    public record TeacherOption(
            long teacherUserId,
            String fullName,
            String status
    ) {
    }

    public record SubjectOption(
            int subjectId,
            String subjectCode,
            String subjectName
    ) {
    }

    public record ClassAssignmentOption(
            long classAssignmentId,
            long classId,
            long teacherUserId,
            int subjectId,
            int academicYearId,
            int gradeLevelId,
            int sectionId,
            String teacherName,
            String subjectName,
            String academicYearName,
            String gradeLevelName,
            String sectionName,
            String status
    ) {
    }

    public record AssessmentOption(
            long testId,
            long testAssignmentId,
            long classAssignmentId,
            int termPeriodId,
            String testName,
            String testType,
            String status,
            String assignmentStatus,
            Instant openAt,
            Instant closeAt
    ) {
    }

    public record StudentOption(
            long studentId,
            long classListId,
            long classId,
            String studentLrn,
            String fullName,
            String enrollmentStatus
    ) {
    }
}
