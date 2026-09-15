package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record V3MfaEnrollmentRequest(
        @NotBlank @Size(max = 128) String password,
        @Size(max = 80) String factorName
) {
}
