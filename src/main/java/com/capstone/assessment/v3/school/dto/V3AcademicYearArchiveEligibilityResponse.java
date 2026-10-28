package com.capstone.assessment.v3.school.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * SET-5: whether the school year can be archived now, and every reason it cannot.
 * readyToArchiveOn is one month after the latest end (the year's end date or a later,
 * extended term end); until then the year stays Completed for late work.
 */
public record V3AcademicYearArchiveEligibilityResponse(
        int academicYearId,
        String status,
        boolean canArchive,
        LocalDate endDate,
        boolean endDateReached,
        int incompleteTermCount,
        int openAssessmentCount,
        List<String> reasons,
        LocalDate latestEndDate,
        LocalDate readyToArchiveOn,
        boolean readyToArchive,
        int pendingSyncCount,
        int unfinalizedResultCount
) {
}
