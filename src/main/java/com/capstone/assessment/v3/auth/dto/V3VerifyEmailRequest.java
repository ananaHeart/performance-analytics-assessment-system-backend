package com.capstone.assessment.v3.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record V3VerifyEmailRequest(
        @NotBlank String challengeUuid,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Verification code must contain exactly 6 digits.")
        String otp,
        // The SMS code; required while an SMS code was sent for this registration.
        @Pattern(regexp = "\\d{6}", message = "SMS code must contain exactly 6 digits.")
        String smsCode
) {
}
