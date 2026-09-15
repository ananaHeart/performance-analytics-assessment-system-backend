package com.capstone.assessment.v2.schoolsetup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record V2ReactivateClassAssignmentRequest(
        @NotBlank(message = "Reactivation reason is required.")
        @Size(min = 10, max = 500, message = "Reactivation reason must contain 10 to 500 characters.")
        String reason
) {
}
