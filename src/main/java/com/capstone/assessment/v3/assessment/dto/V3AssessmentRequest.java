package com.capstone.assessment.v3.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record V3AssessmentRequest(
        @NotNull @Positive Long classAssignmentId,
        @NotNull @Positive Integer termPeriodId,
        @NotBlank @Size(max = 120) String testName,
        @NotBlank @Size(max = 30) String testType,
        String instructions,
        Instant openAt,
        Instant closeAt,
        Boolean allowLateCapture,
        Boolean confirmOutsideClassSchedule,
        @Size(max = 255) String outsideClassScheduleReason,
        @NotEmpty List<@Valid V3AssessmentPartRequest> parts
) {
}
