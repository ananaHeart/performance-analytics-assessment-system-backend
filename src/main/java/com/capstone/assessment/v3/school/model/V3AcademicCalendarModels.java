package com.capstone.assessment.v3.school.model;

import java.time.Instant;
import java.time.LocalDate;

public final class V3AcademicCalendarModels {

    private V3AcademicCalendarModels() {
    }

    public record AcademicYearRow(
            int academicYearId,
            String schoolId,
            int curriculumId,
            String curriculumName,
            String yearName,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TermPeriodRow(
            int termPeriodId,
            int academicYearId,
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
