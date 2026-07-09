package com.capstone.assessment.gradingperiod.dto;

import java.time.LocalDate;

public record CreateGradingPeriodRequest(
        Long academicYearId,
        String periodName,
        Integer periodOrder,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {
}
