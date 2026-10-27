package com.capstone.assessment.v3.account.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** REG-3: replaces both lists; an empty or missing list clears it. */
public record V3TeacherSubjectsRequest(
        @Size(max = 10, message = "Choose up to 10 additional majors.") List<Integer> additionalMajorSubjectIds,
        @Size(max = 10, message = "Choose up to 10 subjects.") List<Integer> taughtSubjectIds
) {
}
