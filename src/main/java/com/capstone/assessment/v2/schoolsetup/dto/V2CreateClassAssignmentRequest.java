package com.capstone.assessment.v2.schoolsetup.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record V2CreateClassAssignmentRequest(
        @NotNull @Positive Long classId,
        @NotNull @Positive Long teacherUserId,
        @NotNull @Positive Integer subjectId,
        @Pattern(regexp = "(?i)primary|co_teacher", message = "Assignment role must be primary or co_teacher.")
        String assignmentRole
) {
}
