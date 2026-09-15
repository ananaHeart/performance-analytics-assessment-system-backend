package com.capstone.assessment.v3.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record V3ClassScheduleStatusRequest(
        @NotBlank @Size(min = 5, max = 255) String reason
) {
}
