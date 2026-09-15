package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record V3RejectTeacherRequest(
        @NotBlank(message = "Rejection reason is required.")
        @Size(min = 5, max = 500, message = "Rejection reason must contain 5 to 500 characters.")
        String reason
) {
}
