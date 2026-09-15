package com.capstone.assessment.v3.school.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record V3SchoolSetupReferenceDataResponse(
        V3SchoolProfileResponse school,
        List<AcademicYearOption> academicYears,
        List<TermPeriodOption> termPeriods,
        List<GradeLevelOption> gradeLevels,
        List<SubjectOption> subjects,
        List<TeacherOption> teachers,
        List<GenderOption> genders,
        List<SuffixOption> suffixes
) {
    public V3SchoolSetupReferenceDataResponse {
        academicYears = List.copyOf(academicYears);
        termPeriods = List.copyOf(termPeriods);
        gradeLevels = List.copyOf(gradeLevels);
        subjects = List.copyOf(subjects);
        teachers = List.copyOf(teachers);
        genders = List.copyOf(genders);
        suffixes = List.copyOf(suffixes);
    }

    public record AcademicYearOption(
            int academicYearId,
            int curriculumId,
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
            String status,
            String activationMode
    ) {
    }

    public record GradeLevelOption(int gradeLevelId, String gradeLevelName) {
    }

    public record SubjectOption(int subjectId, String subjectCode, String subjectName) {
    }

    public record TeacherOption(long teacherUserId, String fullName, String email) {
    }

    public record GenderOption(int genderId, String genderName) {
    }

    public record SuffixOption(int suffixId, String suffixName) {
    }
}
