package com.capstone.assessment.v3.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** CLS-3: the principal edits an assignment's teacher, subject or role; the class stays the same. */
public record V3ClassAssignmentUpdateRequest(
        @NotNull @Positive Long teacherUserId,
        @NotNull @Positive Integer subjectId,
        @NotBlank String assignmentRole,
        @Size(max = 255) String reason
) {
}
