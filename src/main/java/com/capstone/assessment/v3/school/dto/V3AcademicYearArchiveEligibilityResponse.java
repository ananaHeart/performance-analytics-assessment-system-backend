package com.capstone.assessment.v3.school.dto;

import java.time.LocalDate;
import java.util.List;

/** SET-5: whether the school year can be archived (completed) now, and every reason it cannot. */
public record V3AcademicYearArchiveEligibilityResponse(
        int academicYearId,
        String status,
        boolean canArchive,
        LocalDate endDate,
        boolean endDateReached,
        int incompleteTermCount,
        int openAssessmentCount,
        List<String> reasons
) {
}
