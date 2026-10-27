package com.capstone.assessment.v3.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** CLS-7: pause (inactive) or resume (active) a class assignment. */
public record V3ClassAssignmentStatusChangeRequest(
        @NotBlank @Pattern(regexp = "(?i)^(active|inactive)$", message = "Choose active or inactive.")
        String status,
        @NotBlank @Size(min = 5, max = 255) String reason
) {
}
