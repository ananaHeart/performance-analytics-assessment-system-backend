package com.capstone.assessment.v3.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record V3ClassAssignmentRequest(
        @NotNull @Positive Long classId,
        @NotNull @Positive Long teacherUserId,
        @NotNull @Positive Integer subjectId,
        @NotBlank String assignmentRole
) {
}
