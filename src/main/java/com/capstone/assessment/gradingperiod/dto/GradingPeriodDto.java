package com.capstone.assessment.gradingperiod.dto;

import java.time.LocalDate;

public record GradingPeriodDto(
        Long gradingPeriodId,
        Long academicYearId,
        String academicYear,
        String periodName,
        Integer periodOrder,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {
}
