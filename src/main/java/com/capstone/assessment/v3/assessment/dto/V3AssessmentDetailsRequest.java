package com.capstone.assessment.v3.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ASM-7: the safe fields that may change after activation; they do not affect scoring. */
public record V3AssessmentDetailsRequest(
        @NotBlank @Size(max = 120) String testName,
        @Size(max = 10_000) String instructions
) {
}
