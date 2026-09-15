package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record V3ConfirmMfaEnrollmentRequest(
        @NotBlank @Size(max = 36) String factorUuid,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit authenticator code") String code
) {
}
