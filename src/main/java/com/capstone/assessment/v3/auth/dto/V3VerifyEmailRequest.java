package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The registration code, sent by email or SMS (whichever the teacher chose). */
public record V3VerifyEmailRequest(
        @NotBlank String challengeUuid,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Verification code must contain exactly 6 digits.")
        String otp
) {
}
