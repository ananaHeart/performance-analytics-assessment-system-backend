package com.capstone.assessment.v3.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record V3StudentEnrollmentStatusRequest(
        @NotBlank String enrollmentStatus,
        @NotBlank @Size(min = 5, max = 255) String reason
) {
}
