package com.capstone.assessment.v3.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalTime;

public record V3ClassScheduleRequest(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Pattern(regexp = "Asia/Manila") String timezoneName,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
