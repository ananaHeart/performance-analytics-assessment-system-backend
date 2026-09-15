package com.capstone.assessment.v3.schedule.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record V3ClassScheduleResponse(
        long classAssignmentScheduleId,
        String scheduleUuid,
        long classAssignmentId,
        int dayOfWeek,
        String dayName,
        LocalTime startTime,
        LocalTime endTime,
        String timezoneName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String scheduleStatus,
        Long statusChangedByUserId,
        String statusReason,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
