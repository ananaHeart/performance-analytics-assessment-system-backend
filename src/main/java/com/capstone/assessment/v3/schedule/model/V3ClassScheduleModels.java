package com.capstone.assessment.v3.schedule.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class V3ClassScheduleModels {

    private V3ClassScheduleModels() {
    }

    public record AssignmentContext(
            long classAssignmentId,
            long teacherUserId,
            String schoolId,
            String assignmentStatus,
            String classStatus,
            int academicYearId,
            LocalDate academicYearStartDate,
            LocalDate academicYearEndDate
    ) {
    }

    public record ScheduleRow(
            long classAssignmentScheduleId,
            String scheduleUuid,
            long classAssignmentId,
            int dayOfWeek,
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
}
