package com.capstone.assessment.v3.school.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record V3AcademicYearResponse(
        int academicYearId,
        int curriculumId,
        String curriculumName,
        String yearName,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        List<TermPeriod> termPeriods,
        Instant createdAt,
        Instant updatedAt
) {
    public record TermPeriod(
            int termPeriodId,
            String termName,
            int termOrder,
            Instant startAt,
            Instant endAt,
            String status,
            String activationMode,
            Instant activatedAt,
            Instant completedAt,
            Long overriddenByUserId,
            String overrideReason,
            Instant overriddenAt
    ) {
    }
}
