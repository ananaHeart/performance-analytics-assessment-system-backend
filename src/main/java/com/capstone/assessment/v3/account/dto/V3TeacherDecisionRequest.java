package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** TCH-5: the principal reverses a decision, Approved to Rejected or Rejected to Approved. */
public record V3TeacherDecisionRequest(
        @NotBlank @Pattern(regexp = "(?i)^(approved|rejected)$", message = "Choose approved or rejected.")
        String decision,
        @NotBlank(message = "A reason is required.")
        @Size(min = 5, max = 255, message = "The reason must contain 5 to 255 characters.")
        String reason
) {
}
