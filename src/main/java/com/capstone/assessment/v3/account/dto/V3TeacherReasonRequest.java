package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** TCH-4: archive or restore a teacher; the reason is kept in the audit log. */
public record V3TeacherReasonRequest(
        @NotBlank(message = "A reason is required.")
        @Size(min = 5, max = 255, message = "The reason must contain 5 to 255 characters.")
        String reason
) {
}
