package com.capstone.assessment.v2.schoolsetup.dto;

import java.util.List;

public record V2SchoolSetupReferenceResponse(
        List<V2AcademicYearOption> academicYears,
        List<V2GradeLevelOption> gradeLevels,
        List<V2SubjectOption> subjects
) {
}
