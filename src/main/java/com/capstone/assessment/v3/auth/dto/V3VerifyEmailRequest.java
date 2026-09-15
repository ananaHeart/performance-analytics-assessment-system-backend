package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record V3VerifyEmailRequest(
        @NotBlank String challengeUuid,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Verification code must contain exactly 6 digits.")
        String otp
) {
}
