package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** TCH-3: within Approved, the account is Active or Inactive. */
public record V3TeacherStatusRequest(
        @NotBlank @Pattern(regexp = "(?i)^(active|inactive)$", message = "Choose active or inactive.")
        String status,
        @NotBlank(message = "A reason is required.")
        @Size(min = 5, max = 255, message = "The reason must contain 5 to 255 characters.")
        String reason
) {
}
